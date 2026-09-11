package com.wilderness.backend.dto;

import java.time.Instant;

/** 天体生成历史条目（给"历史"页天体生成 tab 的列表用，不含日志字段）。 */
public record CelestialGenerationSummaryDTO(
		Long id,
		String name,
		String type,
		String imageUrl,
		boolean imageTemporary,
		RenderSpec render,
		String agentName,
		boolean success,
		Instant createdAt,
		boolean isPublic) {
}
