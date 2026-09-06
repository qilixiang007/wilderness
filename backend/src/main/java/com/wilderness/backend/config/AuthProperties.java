package com.wilderness.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证相关配置（wilderness.auth.*）。
 * 登录有效期 = sessionTtl（Redis TTL），改配置即可调整，前端无需改动。
 */
@ConfigurationProperties(prefix = "wilderness.auth")
public record AuthProperties(
		String cookieName,          // 会话 Cookie 名，默认 wilderness_session
		long sessionTtl,            // 登录有效期（秒），默认 604800 = 7 天
		boolean cookieSecure,       // Cookie 是否加 Secure（HTTPS 部署时开），默认 false
		long codeTtl,               // 验证码有效期（秒），默认 300 = 5 分钟
		long verifyCodeRateLimit,   // 同一邮箱发码限流间隔（秒），默认 60
		String from,                // 发件人地址；空则用 spring.mail.username
		boolean requireEmailVerify, // 注册是否强制填邮箱验证码，默认 false
		boolean devCodeLog,         // 开发期把验证码打到日志，便于无 SMTP 时手工测试
		int loginMaxAttempts,       // 登录/验证码连续失败多少次触发锁定，默认 5
		long loginLockSeconds) {    // 触发锁定后的锁定时长（秒），默认 900 = 15 分钟
}
