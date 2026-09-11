package com.wilderness.backend.monitor;

import com.wilderness.backend.auth.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时探测 Redis 连通性；只在健康状态发生切换（正常→故障 / 故障→恢复）时邮件通知管理员，
 * 避免每次探测失败都发一封邮件。不用于 docker 容器重启判断——backend 本身是活的，
 * 重启它解决不了 Redis 的问题。
 */
@Component
public class RedisHealthMonitor {

	private static final Logger log = LoggerFactory.getLogger(RedisHealthMonitor.class);
	private static final String PING_KEY = "wilderness:health:ping";
	private static final String ADMIN_EMAIL = "1105341151@qq.com";

	private final StringRedisTemplate redis;
	private final EmailService emailService;
	private final AtomicBoolean healthy = new AtomicBoolean(true);

	public RedisHealthMonitor(StringRedisTemplate redis, EmailService emailService) {
		this.redis = redis;
		this.emailService = emailService;
	}

	@Scheduled(fixedDelay = 30_000)
	public void check() {
		boolean up = ping();
		boolean wasHealthy = healthy.getAndSet(up);
		if (wasHealthy && !up) {
			log.error("Redis 健康检查失败，判定为故障");
			notify("[告警] Redis 故障 - 宇宙是旷野",
					"检测时间：" + Instant.now() + "<br>Redis 连接失败，依赖 Redis 的功能已按预设策略降级/拒绝。");
		} else if (!wasHealthy && up) {
			log.info("Redis 健康检查恢复正常");
			notify("[恢复] Redis 已恢复 - 宇宙是旷野",
					"检测时间：" + Instant.now() + "<br>Redis 连接已恢复正常。");
		}
	}

	private boolean ping() {
		try {
			redis.opsForValue().get(PING_KEY);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void notify(String subject, String body) {
		try {
			emailService.sendAlert(ADMIN_EMAIL, subject, body);
		} catch (Exception e) {
			log.error("Redis 健康状态告警邮件发送失败", e);
		}
	}
}
