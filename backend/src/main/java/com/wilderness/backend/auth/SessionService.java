package com.wilderness.backend.auth;

import com.wilderness.backend.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 登录会话管理：Redis 存 {@code session:{token} → userId}，TTL = 登录有效期。
 * 会话 token 用 32 字节安全随机数生成，碰撞概率可忽略。
 */
@Service
public class SessionService {

	private static final String KEY_PREFIX = "session:";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final StringRedisTemplate redis;
	private final AuthProperties props;

	public SessionService(StringRedisTemplate redis, AuthProperties props) {
		this.redis = redis;
		this.props = props;
	}

	/** 创建会话并返回 token；有效期取配置 session-ttl。 */
	public String createSession(Long userId) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = HexFormat.of().formatHex(bytes);
		redis.opsForValue().set(KEY_PREFIX + token, userId.toString(), Duration.ofSeconds(props.sessionTtl()));
		return token;
	}

	/** 根据 token 解析 userId；过期或不存在返回 null。 */
	public Long getUserId(String token) {
		String value = redis.opsForValue().get(KEY_PREFIX + token);
		return value == null ? null : Long.valueOf(value);
	}

	/** 删除会话（登出）。 */
	public void delete(String token) {
		redis.delete(KEY_PREFIX + token);
	}

	/** 从请求 Cookie 中读取会话 token。 */
	public String tokenFromCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (props.cookieName().equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}
}
