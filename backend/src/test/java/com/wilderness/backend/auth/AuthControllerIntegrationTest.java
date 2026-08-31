package com.wilderness.backend.auth;

import com.wilderness.backend.domain.User;
import com.wilderness.backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口 HTTP 层测试：cookie 写入、会话鉴权、参数校验。
 * Redis 由 mock 提供(H2 + test profile 排除 Redis 自动配置)。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

	@MockitoBean
	private StringRedisTemplate redis;

	@MockitoBean
	private ValueOperations<String, String> valueOps;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void stubRedis() {
		when(redis.opsForValue()).thenReturn(valueOps);
		when(valueOps.setIfAbsent(anyString(), anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
	}

	/** 注册成功：Set-Cookie 写入会话，HttpOnly 且名为 wilderness_session。 */
	@Test
	void registerSetsHttpOnlySessionCookie() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"a@example.com\",\"password\":\"secret123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.email").value("a@example.com"))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("wilderness_session=")))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));
	}

	/** 未登录访问 /me → 401。 */
	@Test
	void meWithoutCookieReturns401() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false));
	}

	/** 会话有效时 /me 返回邮箱(经 mock Redis 还原 userId)。 */
	@Test
	void meWithValidCookieReturnsEmail() throws Exception {
		User u = userRepository.save(new User("me@example.com", "hash"));
		when(valueOps.get("session:valid-token")).thenReturn(String.valueOf(u.getId()));

		mockMvc.perform(get("/api/auth/me").cookie(new Cookie("wilderness_session", "valid-token")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("me@example.com"));
	}

	/** @Valid 邮箱非法 → 400(GlobalExceptionHandler 兜底,不再 500)。 */
	@Test
	void registerWithInvalidEmailReturns400() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"not-an-email\",\"password\":\"secret123\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
	}

	/** 密码登录：密码错误 → 401。 */
	@Test
	void loginPasswordWithWrongPasswordReturns401() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"pw@example.com\",\"password\":\"secret123\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/auth/login-password")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"pw@example.com\",\"password\":\"wrong-pass\"}"))
				.andExpect(status().isUnauthorized());
	}

	/** 验证码登录：验证码匹配(经 mock Redis) + 用户存在 → 200 且写 cookie。 */
	@Test
	void loginCodeWithValidCodeSucceeds() throws Exception {
		userRepository.save(new User("code@example.com", "hash"));
		when(valueOps.get("verify:code@example.com:login")).thenReturn("123456");

		mockMvc.perform(post("/api/auth/login-code")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"code@example.com\",\"code\":\"123456\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("wilderness_session=")));
	}

	/** 验证码错误 → 400。 */
	@Test
	void loginCodeWithWrongCodeReturns400() throws Exception {
		userRepository.save(new User("code2@example.com", "hash"));
		when(valueOps.get("verify:code2@example.com:login")).thenReturn("123456");

		mockMvc.perform(post("/api/auth/login-code")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"code2@example.com\",\"code\":\"999999\"}"))
				.andExpect(status().isBadRequest());
	}
}
