package com.wilderness.backend.dto;

import java.util.List;

/**
 * 天体详情：基本信息 + 所属分类 + 数据卡片。
 */
public record ObjectDetailDTO(
		String slug,
		String zhName,
		String enName,
		String zhDescription,
		String enDescription,
		String image,
		int sortOrder,
		CategoryRefDTO category,
		List<FactDTO> facts) {
}
