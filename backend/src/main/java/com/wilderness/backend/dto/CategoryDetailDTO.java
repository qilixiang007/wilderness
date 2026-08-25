package com.wilderness.backend.dto;

import java.util.List;

/**
 * 分类详情：分类信息 + 其下的天体列表。
 */
public record CategoryDetailDTO(
		String slug,
		String zhName,
		String enName,
		String zhDescription,
		String enDescription,
		String image,
		String imageAltZh,
		String imageAltEn,
		List<ObjectSummaryDTO> objects) {
}
