package com.wilderness.backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * 对话记录的 MQ / ES 载荷（RocketMQ 事件 + Elasticsearch 文档共用）。
 * 与 {@link ConversationMessageDTO} 相同内容但去掉表现层细节，保持事件字段纯净。
 */
public record ConversationMessageEvent(
		Long id,
		Long userId,
		String question,
		String answer,
		List<AiSource> sources,
		boolean webEnabled,
		Instant createdAt) {
}
