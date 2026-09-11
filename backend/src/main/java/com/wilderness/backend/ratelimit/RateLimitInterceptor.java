package com.wilderness.backend.ratelimit;

import com.wilderness.backend.auth.AuthContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Map;

/**
 * 接口限流（/api/ai/** + /api/knowledge/upload）：登录用户按 userId、未登录按 IP，
 * 按接口分组分别定阈值；/api/ai/** 另加一个不分用户/IP 的全局兜底（知识库上传不计入，
 * 是完全不同的资源消耗类型）。必须注册在 AuthInterceptor 之后（见 WebConfig 里的 order），
 * 这样 preHandle 时 AuthContext.currentUserId() 已经可用。触发限流直接抛
 * ResponseStatusException(429)，交给全局异常处理器统一包装，不需要新增异常类型。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
	private static final int WINDOW_SECONDS = 60;
	private static final int GLOBAL_LIMIT = 200;

	/**
	 * path 前缀 -> [登录用户限额, 未登录(IP)限额]。
	 * 用 List 而不是 Map 保证匹配顺序——更具体的前缀（如 /api/ai/chat/stream）
	 * 必须排在更短的前缀（/api/ai/chat）之前，否则会被误判分组。
	 */
	private static final List<Map.Entry<String, int[]>> LIMITS = List.of(
			Map.entry("/api/ai/compare/stream", new int[]{3, 2}),
			Map.entry("/api/ai/chat/stream", new int[]{10, 5}),
			Map.entry("/api/ai/generate-celestial", new int[]{5, 2}),
			Map.entry("/api/ai/explain/", new int[]{8, 4}),
			Map.entry("/api/ai/chat", new int[]{10, 5}),
			// /api/knowledge/upload 本身在 AuthInterceptor.REQUIRED 里强制登录，
			// 未登录(IP)这档分支实际不会被触发，两个数字给一样的值即可。
			Map.entry("/api/knowledge/upload", new int[]{5, 5})
	);

	private final SlidingWindowRateLimiter limiter;

	public RateLimitInterceptor(SlidingWindowRateLimiter limiter) {
		this.limiter = limiter;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// SSE 接口（chatStream/compareStream）返回 SseEmitter，走 Servlet 异步分发：
		// 异步完成时容器会再触发一次 ASYNC 类型的重新分发，拦截器链会跟着重新走一遍。
		// 不跳过的话，同一个逻辑请求会被计数两次（实测验证过：未登录 compare/stream
		// 阈值设的是 2，第 1 次请求就写入了 2 条计数记录，导致第 2 次请求直接被拒）。
		// 只在最初的 REQUEST 阶段判断一次即可。
		if (request.getDispatcherType() == DispatcherType.ASYNC) {
			return true;
		}

		Map.Entry<String, int[]> matched = resolve(request.getRequestURI());
		if (matched == null) {
			return true;
		}

		// 全局兜底桶只统计 /api/ai/** 的量，知识库上传是完全不同的资源消耗类型，
		// 不该跟 AI 请求量共用同一个计数器（否则上传高峰会误伤正常 AI 请求，反之亦然）。
		boolean isAiPath = matched.getKey().startsWith("/api/ai/");
		Long userId = AuthContext.currentUserId();
		String identifier = userId != null ? "user:" + userId : "ip:" + clientIp(request);
		int limit = userId != null ? matched.getValue()[0] : matched.getValue()[1];

		try {
			if (!limiter.tryAcquire("ai:ratelimit:" + identifier + ":" + matched.getKey(), limit, WINDOW_SECONDS)) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
			}
			if (isAiPath && !limiter.tryAcquire("ai:ratelimit:global", GLOBAL_LIMIT, WINDOW_SECONDS)) {
				throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "系统当前 AI 请求量较大，请稍后再试");
			}
		} catch (ResponseStatusException e) {
			throw e;
		} catch (Exception e) {
			log.warn("Redis 限流查询失败，按超限处理 path={}", request.getRequestURI(), e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "服务暂时不可用，请稍后再试");
		}
		return true;
	}

	private Map.Entry<String, int[]> resolve(String path) {
		for (Map.Entry<String, int[]> entry : LIMITS) {
			if (path.startsWith(entry.getKey())) {
				return entry;
			}
		}
		return null;
	}

	private String clientIp(HttpServletRequest request) {
		String ip = request.getHeader("X-Real-IP");
		return (ip != null && !ip.isBlank()) ? ip : request.getRemoteAddr();
	}
}
