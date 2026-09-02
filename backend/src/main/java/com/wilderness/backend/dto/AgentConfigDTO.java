package com.wilderness.backend.dto;

import java.time.Instant;

/** 用户自定义智能体（列表/创建/更新返回）。 */
public record AgentConfigDTO(
		Long id,
		String name,
		String systemPrompt,
		boolean knowledgeSearchEnabled,
		boolean imageGenEnabled,
		Instant createdAt,
		Instant updatedAt) {
}
