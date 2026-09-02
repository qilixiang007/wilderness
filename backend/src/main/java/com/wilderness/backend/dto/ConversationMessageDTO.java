package com.wilderness.backend.dto;

import java.time.Instant;
import java.util.List;

/** 对话历史条目（给前端 /api/history 列表用）。 */
public record ConversationMessageDTO(
		Long id,
		String question,
		String answer,
		List<AiSource> sources,
		boolean webEnabled,
		Instant createdAt) {
}
