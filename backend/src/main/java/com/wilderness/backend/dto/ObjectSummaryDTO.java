package com.wilderness.backend.dto;

/**
 * 天体列表项：分类详情、对象列表和搜索结果共用。
 */
public record ObjectSummaryDTO(
		String slug,
		String zhName,
		String enName,
		String zhDescription,
		String enDescription,
		String image,
		int sortOrder) {
}
