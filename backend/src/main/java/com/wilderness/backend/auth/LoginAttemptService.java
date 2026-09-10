package com.wilderness.backend.auth;

import com.wilderness.backend.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 * 登录 / 验证码防暴力破解：同一 key（邮箱，或"邮箱:用途"）连续失败达到阈值后锁定一段时间。
 * Redis 键：
 * - {@code login:fail:{key}} → 失败计数，首次失败起 TTL=lock-seconds（计数窗口）
 * - {@code login:lock:{key}} → 锁定标记，TTL=lock-seconds
 */
@Service
public class LoginAttemptService {

	private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
	private static final String FAIL_KEY = "login:fail:";
	private static final String LOCK_KEY = "login:lock:";

	private final StringRedisTemplate redis;
	private final AuthProperties props;

	public LoginAttemptService(StringRedisTemplate redis, AuthProperties props) {
		this.redis = redis;
		this.props = props;
	}

	/** 已被锁定则抛 429，提示剩余等待分钟数。Redis 查不了时 fail open：当作未锁定放行。 */
	public void assertNotLocked(String key) {
		Long ttl;
		try {
			ttl = redis.getExpire(LOCK_KEY + key);
		} catch (Exception e) {
			log.warn("Redis 锁定状态查询失败，按未锁定放行 key={}", key, e);
			return;
		}
		if (ttl != null && ttl > 0) {
			long minutes = (ttl + 59) / 60;
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"失败次数过多，请 " + minutes + " 分钟后再试");
		}
	}

	/** 记录一次失败；达到阈值则锁定 lock-seconds。Redis 挂了则尽力而为，静默跳过。 */
	public void onFailure(String key) {
		try {
			String failKey = FAIL_KEY + key;
			Long count = redis.opsForValue().increment(failKey);
			if (count != null && count == 1L) {
				redis.expire(failKey, Duration.ofSeconds(props.loginLockSeconds()));
			}
			if (count != null && count >= props.loginMaxAttempts()) {
				redis.opsForValue().set(LOCK_KEY + key, "1", Duration.ofSeconds(props.loginLockSeconds()));
				redis.delete(failKey);
			}
		} catch (Exception e) {
			log.warn("Redis 失败计数记录失败，跳过 key={}", key, e);
		}
	}

	/** 成功后清除失败计数与锁定状态。Redis 挂了则尽力而为，静默跳过。 */
	public void onSuccess(String key) {
		try {
			redis.delete(FAIL_KEY + key);
			redis.delete(LOCK_KEY + key);
		} catch (Exception e) {
			log.warn("Redis 失败计数清除失败，跳过 key={}", key, e);
		}
	}
}
