package com.wilderness.backend.dto;

import java.time.Instant;
import java.util.Map;

/** 收藏的生成天体快照，字段形状与前端渲染所需一致。 */
public record FavoriteGenerationDTO(
		Long generationId,
		String name,
		String type,
		Map<String, String> parameters,
		String introduction,
		RenderSpec render,
		String imageUrl,
		Instant createdAt) {
}
