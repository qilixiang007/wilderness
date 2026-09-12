package com.wilderness.backend.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AuthInterceptor 对 AuthContext(ThreadLocal) 的写入/清理契约。
 * Tomcat 线程复用，任何一条「写了不擦」的路径都会让下一个请求串用上一位用户的身份，
 * 这里逐条锁住：进门无条件重置 + 同步/异步两种出口都清理。
 */
class AuthInterceptorTest {

	private final SessionService sessionService = mock(SessionService.class);

	private final AuthInterceptor interceptor = new AuthInterceptor(sessionService);

	private final MockHttpServletResponse response = new MockHttpServletResponse();

	@AfterEach
	void clearContext() {
		// 用例跑在同一条线程上，不清干净会互相污染
		AuthContext.clear();
	}

	/**
	 * 进门无条件重置：上一个请求残留的 userId 必须在匿名请求的 preHandle 里被擦掉。
	 * 这是防越权的底线——不依赖上一个请求有没有清理干净。
	 */
	@Test
	void preHandleClearsLeftoverUserIdForAnonymousRequest() {
		when(sessionService.tokenFromCookie(any())).thenReturn(null);
		AuthContext.setUserId(99L); // 模拟上一个 SSE 请求残留在本线程上的身份

		interceptor.preHandle(new MockHttpServletRequest("GET", "/api/ai/chat"), response, null);

		assertNull(AuthContext.currentUserId(), "匿名请求不得读到上一个请求残留的 userId");
	}

	/**
	 * 重置必须排在 OPTIONS 的 early return 之前：预检请求到不了 Controller，
	 * 但仍会继续走 RateLimitInterceptor，那里要读 AuthContext——
	 * 残留值会让匿名预检消耗上一位用户的限流配额。
	 */
	@Test
	void preHandleClearsLeftoverUserIdEvenForOptionsRequest() {
		AuthContext.setUserId(99L);

		interceptor.preHandle(new MockHttpServletRequest("OPTIONS", "/api/ai/chat/stream"), response, null);

		assertNull(AuthContext.currentUserId(), "OPTIONS 提前放行之前也必须先重置");
	}

	/**
	 * SSE 异步出口：handler 返回 SseEmitter 后容器立刻释放请求线程，
	 * 此时走的是本回调而非 afterCompletion。不在这里清理，
	 * userId 就会跟着线程回到 Tomcat 线程池，被下一个复用该线程的请求读到。
	 */
	@Test
	void afterConcurrentHandlingStartedClearsUserId() throws Exception {
		assertInstanceOf(AsyncHandlerInterceptor.class, interceptor,
				"AuthInterceptor 必须实现 AsyncHandlerInterceptor，否则 SSE 异步启动后不会触发任何清理回调");
		AuthContext.setUserId(99L);

		((AsyncHandlerInterceptor) interceptor).afterConcurrentHandlingStarted(
				new MockHttpServletRequest("GET", "/api/ai/chat/stream"), response, null);

		assertNull(AuthContext.currentUserId(), "SSE 异步启动后必须清理，否则残留在 Tomcat 线程上");
	}

	/** 同步出口：原有行为，不能回退。 */
	@Test
	void afterCompletionClearsUserId() {
		AuthContext.setUserId(99L);

		interceptor.afterCompletion(new MockHttpServletRequest("GET", "/api/favorites"), response, null, null);

		assertNull(AuthContext.currentUserId());
	}
}
