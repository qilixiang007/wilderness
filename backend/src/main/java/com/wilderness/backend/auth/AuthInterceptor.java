package com.wilderness.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 鉴权拦截器（不引入 Spring Security 的实现方式）：
 * 对所有 /api/** 请求「可选解析」登录态——有会话 Cookie 就把 userId 写入
 * {@link AuthContext}，没有则保持未登录；仅对 REQUIRED 路径强制要求登录。
 * 401 异常交给全局异常处理器统一包装为 ApiResponse。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

	private static final String[] REQUIRED = {
			"/api/favorites/**",
			"/api/knowledge/upload",
			"/api/knowledge/files/**",
			"/api/knowledge/reingest",
			"/api/history/**",
			"/api/agents/**",
			"/api/traces",
			"/api/traces/**",
			"/api/auth/logout",
			"/api/auth/me",
			"/api/auth/change-password"
	};

	private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

	private final SessionService sessionService;

	public AuthInterceptor(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		// CORS 预检请求直接放行
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		Long userId = null;
		String token = sessionService.tokenFromCookie(request);
		if (token != null && !token.isBlank()) {
			try {
				userId = sessionService.getUserId(token);
			} catch (Exception e) {
				log.warn("Redis 会话查询失败，按未登录降级处理 path={}", request.getRequestURI(), e);
				if (requiresAuth(request.getRequestURI())) {
					throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "服务暂时不可用，请稍后再试");
				}
			}
		}
		if (userId != null) {
			AuthContext.setUserId(userId);
		}
		if (requiresAuth(request.getRequestURI()) && userId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
		}
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
			Object handler, Exception ex) {
		AuthContext.clear();
	}

	private boolean requiresAuth(String path) {
		return Arrays.stream(REQUIRED).anyMatch(p -> PATH_MATCHER.match(p, path));
	}
}
