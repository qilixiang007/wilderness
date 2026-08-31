package com.wilderness.backend.auth;

import com.wilderness.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 注册 / 登录业务逻辑测试。
 * test profile 排除了 Redis，这里用 mock 提供 StringRedisTemplate，
 * 用户表走真实 H2，验证码/会话存取走 mock 的 Redis 语义。
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthServiceTest {

	@MockitoBean
	private StringRedisTemplate redis;

	@MockitoBean
	private ValueOperations<String, String> valueOps;

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void stubRedis() {
		when(redis.opsForValue()).thenReturn(valueOps);
		// set(K,V,Duration) 是 void,会话写入无需 stub;setIfAbsent 用于验证码限流(默认放行)
		when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
	}

	/** 注册：BCrypt 落库 + 注册即登录(返回会话 token)。 */
	@Test
	void registerPersistsUserAndReturnsSession() {
		LoginResult result = authService.register("Alice@Example.com", "secret123", null);

		assertNotNull(result.token());
		// 邮箱归一化:trim + 小写
		assertTrue(userRepository.existsByEmail("alice@example.com"));
		assertEquals("alice@example.com", result.user().email());
	}

	/** 重复邮箱注册 → 409。 */
	@Test
	void registerDuplicateEmailThrows409() {
		authService.register("dup@example.com", "secret123", null);

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> authService.register("dup@example.com", "secret456", null));
		assertEquals(409, ex.getStatusCode().value());
	}

	/** 密码不足 6 位 → 400。 */
	@Test
	void registerShortPasswordThrows400() {
		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> authService.register("short@example.com", "123", null));
		assertEquals(400, ex.getStatusCode().value());
	}

	/** 密码登录：密码错误 → 401；正确 → 返回会话。 */
	@Test
	void loginPasswordValidatesCredential() {
		authService.register("pwd@example.com", "secret123", null);

		ResponseStatusException wrong = assertThrows(ResponseStatusException.class,
				() -> authService.loginPassword("pwd@example.com", "wrong-pass"));
		assertEquals(401, wrong.getStatusCode().value());

		LoginResult ok = authService.loginPassword("pwd@example.com", "secret123");
		assertNotNull(ok.token());
		assertEquals("pwd@example.com", ok.user().email());
	}

	/** 验证码登录：验证码匹配(经 mock Redis)且用户存在 → 成功。 */
	@Test
	void loginCodeWithValidCodeSucceeds() {
		authService.register("code@example.com", "secret123", null);
		when(valueOps.get("verify:code@example.com:login")).thenReturn("123456");

		LoginResult ok = authService.loginCode("code@example.com", "123456");
		assertNotNull(ok.token());
	}

	/** 验证码登录：验证码错误 → 400。 */
	@Test
	void loginCodeWithWrongCodeThrows400() {
		authService.register("code2@example.com", "secret123", null);
		when(valueOps.get("verify:code2@example.com:login")).thenReturn("123456");

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> authService.loginCode("code2@example.com", "999999"));
		assertEquals(400, ex.getStatusCode().value());
	}

	/** 验证码登录：验证码正确但用户不存在 → 401 提示先注册。 */
	@Test
	void loginCodeForUnknownUserThrows401() {
		when(valueOps.get("verify:ghost@example.com:login")).thenReturn("123456");

		ResponseStatusException ex = assertThrows(ResponseStatusException.class,
				() -> authService.loginCode("ghost@example.com", "123456"));
		assertEquals(401, ex.getStatusCode().value());
	}

	/** 登出：空 token / null 静默成功(幂等)。 */
	@Test
	void logoutIsIdempotent() {
		authService.logout("some-token");
		authService.logout("");
		authService.logout(null);
	}
}
