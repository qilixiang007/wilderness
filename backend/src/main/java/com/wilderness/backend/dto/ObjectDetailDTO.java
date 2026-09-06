package com.wilderness.backend.dto;

import java.util.List;

/**
 * 天体详情：基本信息 + 所属分类 + 数据卡片。
 * source/sourceUrl/sourcedAt 可空：仅权威同步过的对象有值（其余对象不填，前端不显示）。
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
		List<FactDTO> facts,
		String source,
		String sourceUrl,
		String sourcedAt) {
}
