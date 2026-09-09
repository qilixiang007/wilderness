package com.wilderness.backend.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 滑动窗口限流：Redis ZSET 存每次请求的时间戳做"日志"，每次请求前先清理窗口外的
 * 旧记录再计数，避免固定窗口（INCR+EXPIRE）常见的边界突刺问题。
 * 清理+计数+写入用 Lua 脚本在 Redis 端原子执行，避免高并发下的竞态条件。
 */
@Component
public class SlidingWindowRateLimiter {

	private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
			local key = KEYS[1]
			local now = tonumber(ARGV[1])
			local window = tonumber(ARGV[2])
			local limit = tonumber(ARGV[3])

			redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
			local count = redis.call('ZCARD', key)
			if count >= limit then
			    return 0
			end
			redis.call('ZADD', key, now, ARGV[4])
			redis.call('EXPIRE', key, math.ceil(window / 1000))
			return 1
			""", Long.class);

	private final StringRedisTemplate redis;

	public SlidingWindowRateLimiter(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** 窗口 windowSeconds 秒内最多放行 limit 次；超限返回 false。 */
	public boolean tryAcquire(String key, int limit, int windowSeconds) {
		long now = System.currentTimeMillis();
		long windowMillis = windowSeconds * 1000L;
		String member = now + "-" + UUID.randomUUID();
		Long result = redis.execute(SCRIPT, List.of(key),
				String.valueOf(now), String.valueOf(windowMillis), String.valueOf(limit), member);
		return result != null && result == 1L;
	}
}
