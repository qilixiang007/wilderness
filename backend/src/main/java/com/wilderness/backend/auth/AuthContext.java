package com.wilderness.backend.auth;

/**
 * 当前请求的登录用户上下文（ThreadLocal）。
 * 由 {@link AuthInterceptor} 在 preHandle 开头无条件重置后写入；
 * 同步请求在 afterCompletion 清理，异步（SSE）请求在 afterConcurrentHandlingStarted 清理。
 * Tomcat 线程复用，任何一条「写了不擦」的路径都会让下一个请求串用上一位用户的身份，
 * 所以 preHandle 的无条件重置是底线——不依赖上一个请求有没有清理干净。
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
