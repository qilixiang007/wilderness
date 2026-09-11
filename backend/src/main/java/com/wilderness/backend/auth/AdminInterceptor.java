package com.wilderness.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员拦截器：只处理标了 {@link AdminOnly} 的接口，其余直接放行。
 * 必须排在 {@link AuthInterceptor} 之后——依赖它写入 AuthContext 的 userId。
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

	private final AuthService authService;

	public AdminInterceptor(AuthService authService) {
		this.authService = authService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod method) || !requiresAdmin(method)) {
			return true;
		}
		Long userId = AuthContext.currentUserId();
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
		}
		if (!authService.isAdminUser(userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员权限");
		}
		return true;
	}

	private boolean requiresAdmin(HandlerMethod method) {
		return method.hasMethodAnnotation(AdminOnly.class)
				|| method.getBeanType().isAnnotationPresent(AdminOnly.class);
	}
}
