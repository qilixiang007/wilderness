package com.wilderness.backend.dto;

import java.time.Instant;

/** 公开广场条目：只含"成品"字段，不含生成者身份、不含任何链路调试字段。 */
public record GalleryItemDTO(
		Long id,
		String name,
		String type,
		String introduction,
		String imageUrl,
		boolean imageTemporary,
		RenderSpec render,
		Instant createdAt) {
}
