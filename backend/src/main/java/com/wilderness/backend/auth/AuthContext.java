package com.wilderness.backend.auth;

/**
 * 当前请求的登录用户上下文（ThreadLocal）。
 * 由 {@link AuthInterceptor} 在请求线程写入、afterCompletion 清理。
 * 注意：SSE 等跨线程执行的任务必须在请求线程读取后作为参数传入，
 * 不能在线程池线程里读取（ThreadLocal 不跨线程）。
 */
public final class AuthContext {

	private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

	private AuthContext() {
	}

	public static void setUserId(Long id) {
		USER_ID.set(id);
	}

	/** 当前登录用户 id；未登录为 null。 */
	public static Long currentUserId() {
		return USER_ID.get();
	}

	public static void clear() {
		USER_ID.remove();
	}
}
