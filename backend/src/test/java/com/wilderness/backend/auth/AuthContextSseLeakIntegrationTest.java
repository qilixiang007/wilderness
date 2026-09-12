package com.wilderness.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 真实 Tomcat 下复现「SSE 请求后 AuthContext 残留、匿名请求串用他人身份」。
 *
 * <p>为什么必须起真实容器：MockMvc 不跑 Tomcat，异步靠测试代码手工 asyncDispatch() 驱动、
 * 全程单线程，压根没有「线程归还池子再被复用」这回事，断言会恒真。
 *
 * <p>为什么把 Tomcat 线程数压到 1：保证第二个请求必然复用处理过 SSE 的那条线程。
 * min-spare 必须一起设——Tomcat 内部是 ThreadPoolExecutor，min-spare 是 corePoolSize、
 * max 是 maximumPoolSize，只设 max 会 core(10) > max(1) 直接起不来。
 *
 * <p>为什么用测试专用 Controller 而不是 /api/ai/chat/stream：AiController 有
 * @ConditionalOnExpression，test profile 没有 DashScope key 时整个类不注册。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"server.tomcat.threads.max=1",
				"server.tomcat.threads.min-spare=1"
		})
@ActiveProfiles("test")
class AuthContextSseLeakIntegrationTest {

	private static final String TOKEN = "test-token-42";

	private static final Long USER_ID = 42L;

	@MockitoBean
	private StringRedisTemplate redis;

	@MockitoBean
	private SessionService sessionService;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private LeakProbeController probe;

	/**
	 * 时序是这个用例成立的前提：第二个请求必须在 SSE 还没结束时发出。
	 * 若等 SSE 跑完再发，emitter.complete() 触发的 ASYNC 重新分发会走一遍
	 * afterCompletion 把残留值擦掉——在 threads.max=1 下更是必然擦掉（只有一条线程），
	 * 用例在未修复的代码上也会是绿的，等于白写。
	 */
	@Test
	void anonymousRequestMustNotInheritUserIdFromPrecedingSseRequest() throws Exception {
		// 带 X-Test-Token 头的请求视为已登录用户 42，不带的视为匿名
		when(sessionService.tokenFromCookie(any())).thenAnswer(invocation -> {
			HttpServletRequest request = invocation.getArgument(0);
			return request.getHeader("X-Test-Token");
		});
		when(sessionService.getUserId(anyString())).thenReturn(null);
		when(sessionService.getUserId(TOKEN)).thenReturn(USER_ID);

		ExecutorService client = Executors.newSingleThreadExecutor();
		try {
			// 1. 以用户 42 的身份发起 SSE，连接保持打开
			HttpHeaders headers = new HttpHeaders();
			headers.set("X-Test-Token", TOKEN);
			headers.setAccept(java.util.List.of(MediaType.TEXT_EVENT_STREAM));
			client.submit(() -> restTemplate.exchange("/api/leak-probe/stream", HttpMethod.GET,
					new HttpEntity<>(headers), String.class));

			// 2. 等服务端推出 ready 事件，确认 handler 已返回 SseEmitter、异步已启动
			assertTrue(probe.started.await(10, TimeUnit.SECONDS), "SSE 未能在 10s 内启动");

			// 3. SSE 仍在进行中，此时发匿名请求——threads.max=1 保证它复用同一条 Tomcat 线程
			String seen = restTemplate.getForObject("/api/leak-probe/whoami", String.class);

			// 4. 匿名请求不得看到用户 42
			assertEquals("null", seen,
					"匿名请求读到了上一个 SSE 请求残留在 Tomcat 线程上的 userId");
		} finally {
			probe.release.countDown();
			client.shutdownNow();
		}
	}

	@TestConfiguration
	static class ProbeConfig {

		@Bean
		LeakProbeController leakProbeController() {
			return new LeakProbeController();
		}
	}

	/**
	 * 探针接口。路径必须落在 /api/** 下才会走 AuthInterceptor，
	 * 且不能命中 AuthInterceptor 的 REQUIRED 名单（否则匿名请求会被 401 挡掉）。
	 */
	@RestController
	@RequestMapping("/api/leak-probe")
	static class LeakProbeController {

		final CountDownLatch started = new CountDownLatch(1);

		final CountDownLatch release = new CountDownLatch(1);

		private final ExecutorService worker = Executors.newSingleThreadExecutor();

		/** 推一个 ready 事件后挂住，由测试决定何时收尾——模拟 AI 生成期间的泄漏窗口。 */
		@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
		SseEmitter stream() {
			SseEmitter emitter = new SseEmitter(0L);
			worker.execute(() -> {
				try {
					emitter.send(SseEmitter.event().name("ready").data("ok"));
					started.countDown();
					release.await(15, TimeUnit.SECONDS);
					emitter.complete();
				} catch (Exception e) {
					emitter.completeWithError(e);
				}
			});
			return emitter;
		}

		/** 直接把线程上的 AuthContext 暴露出来。 */
		@GetMapping("/whoami")
		String whoami() {
			Long userId = AuthContext.currentUserId();
			return userId == null ? "null" : String.valueOf(userId);
		}
	}
}
