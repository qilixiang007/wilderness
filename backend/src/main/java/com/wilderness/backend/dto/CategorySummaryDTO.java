package com.wilderness.backend.dto;

/**
 * 分类列表项：附带该分类下的天体数量。
 */
public record CategorySummaryDTO(
		String slug,
		String zhName,
		String enName,
		String zhDescription,
		String enDescription,
		String image,
		String imageAltZh,
		String imageAltEn,
		long objectCount) {
}
