package com.wilderness.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.ai.CompareService;
import com.wilderness.backend.ai.RagService;
import com.wilderness.backend.ai.agent.CelestialAgentService;
import com.wilderness.backend.ratelimit.SlidingWindowRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 真实 Tomcat 下验证 SSE 超时确实会触发并关掉连接。
 *
 * <p>为什么非要起容器:脱离容器时 SseEmitter 没有 initialize,handler 为 null,
 * onTimeout/onCompletion 回调根本不会被调用,单测断言不到超时链路。
 *
 * <p>为什么要塞假 key:AiController 有 @ConditionalOnExpression,
 * test profile 没有 DashScope key 时整个类不注册,端点会是 404。
 *
 * <p>为什么要覆盖 bean:生产超时是 180s,测试等不起,用同名 bean 覆盖注入 2s。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"wilderness.ai.dashscope.api-key=dummy-key-for-test",
				"spring.main.allow-bean-definition-overriding=true"
		})
@ActiveProfiles("test")
class AiControllerTimeoutIntegrationTest {

	private static final long TEST_TIMEOUT_MS = 2_000L;

	@MockitoBean
	private StringRedisTemplate redis;

	/** 什么都不做 —— 模拟上游模型卡死:一个回调都不来,emitter 永远等不到 complete。 */
	@MockitoBean
	private RagService ragService;

	@MockitoBean
	private CompareService compareService;

	@MockitoBean
	private CelestialAgentService celestialAgentService;

	/** 限流器真身要查 Redis,而 test profile 的 Redis 是 mock 的,不放行就会被判 503。 */
	@MockitoBean
	private SlidingWindowRateLimiter rateLimiter;

	@Autowired
	private TestRestTemplate restTemplate;

	@BeforeEach
	void passRateLimit() {
		when(rateLimiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(true);
	}

	/**
	 * 上游一个字都不吐时,连接必须在超时后被关掉,而不是永远挂着。
	 * 改动前是 new SseEmitter(0L)(永不超时),这个请求会一直阻塞到测试超时。
	 */
	@Test
	@Timeout(30)
	void hungUpstreamConnectionIsClosedOnTimeout() {
		long start = System.currentTimeMillis();

		ResponseEntity<String> response =
				restTemplate.getForEntity("/api/ai/chat/stream?question=上游卡死不吐字", String.class);

		long elapsed = System.currentTimeMillis() - start;
		assertEquals(HttpStatus.OK, response.getStatusCode(),
				"没进到 SSE 逻辑就返回了,先确认没被鉴权/限流拦掉");
		assertTrue(elapsed >= TEST_TIMEOUT_MS - 500,
				"不应立即返回,应该等到超时才关闭,实际 " + elapsed + "ms");
		assertTrue(elapsed < 15_000,
				"超时后应及时关闭连接,而不是一直挂着,实际 " + elapsed + "ms");
	}

	@TestConfiguration
	static class ShortTimeoutConfig {

		/** 同名 bean 覆盖掉组件扫描出来的那个,把 180s/150s 换成 2s。 */
		@Bean
		AiController aiController(RagService ragService,
				CelestialAgentService celestialAgentService,
				CompareService compareService,
				ObjectMapper objectMapper,
				@Qualifier("aiStreamExecutor") Executor streamExecutor) {
			return new AiController(ragService, celestialAgentService, compareService, objectMapper,
					streamExecutor, TEST_TIMEOUT_MS, TEST_TIMEOUT_MS);
		}
	}
}
