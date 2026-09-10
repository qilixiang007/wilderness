package com.wilderness.backend.auth;

import com.wilderness.backend.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 邮箱验证码：发送（限流）与校验（一次性）。
 * Redis 键：
 * - {@code verify:{email}:{purpose}} → 6 位数字码，TTL=code-ttl
 * - {@code verify:ratelimit:{email}} → 限流标记，TTL=verify-code-rate-limit
 */
@Service
public class VerifyCodeService {

	private static final Logger log = LoggerFactory.getLogger(VerifyCodeService.class);
	private static final String CODE_KEY = "verify:";
	private static final String RATE_KEY = "verify:ratelimit:";

	private final StringRedisTemplate redis;
	private final AuthProperties props;
	private final EmailService emailService;
	private final LoginAttemptService loginAttemptService;

	public VerifyCodeService(StringRedisTemplate redis, AuthProperties props, EmailService emailService,
			LoginAttemptService loginAttemptService) {
		this.redis = redis;
		this.props = props;
		this.emailService = emailService;
		this.loginAttemptService = loginAttemptService;
	}

	/**
	 * 发送验证码到指定邮箱；同一邮箱在限流间隔内重复调用返回 429。
	 * 返回投递方式：email=已发真实邮件；log=开发模式，验证码只打进服务端日志、未发邮件。
	 * 调用方应据此如实告知用户验证码在哪，绝不虚构"已发送到邮箱"。
	 */
	public String sendCode(String email, String purpose) {
		Boolean first;
		try {
			first = redis.opsForValue()
					.setIfAbsent(RATE_KEY + email, "1", Duration.ofSeconds(props.verifyCodeRateLimit()));
		} catch (Exception e) {
			log.error("验证码限流查询失败 email={}", email, e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "验证码服务暂时不可用，请稍后再试");
		}
		if (Boolean.FALSE.equals(first)) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "发送太频繁，请稍后再试");
		}
		String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
		try {
			redis.opsForValue().set(CODE_KEY + email + ":" + purpose, code, Duration.ofSeconds(props.codeTtl()));
		} catch (Exception e) {
			log.error("验证码存储失败 email={}", email, e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "验证码服务暂时不可用，请稍后再试");
		}
		if (props.devCodeLog()) {
			log.info("[dev-code-log] 验证码 email={} purpose={} code={}（开发模式：未发送邮件）", email, purpose, code);
			return "log";
		}
		emailService.sendCode(email, code);
		return "email";
	}

	/**
	 * 校验验证码；错误/过期抛 400。校验成功即删除（一次性使用）。
	 * 同一"邮箱+用途"连续猜错达到阈值会被锁定一段时间，防止暴力穷举 6 位验证码。
	 */
	public void verify(String email, String purpose, String code) {
		String attemptKey = email + ":" + purpose;
		loginAttemptService.assertNotLocked(attemptKey);
		String key = CODE_KEY + email + ":" + purpose;
		String expected;
		try {
			expected = redis.opsForValue().get(key);
		} catch (Exception e) {
			log.error("验证码查询失败 email={}", email, e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "验证码服务暂时不可用，请稍后再试");
		}
		if (expected == null || code == null || !expected.equals(code)) {
			loginAttemptService.onFailure(attemptKey);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "验证码错误或已过期");
		}
		loginAttemptService.onSuccess(attemptKey);
		try {
			redis.delete(key);
		} catch (Exception e) {
			log.warn("验证码已校验通过，但删除一次性 key 失败 email={}", email, e);
		}
	}
}
