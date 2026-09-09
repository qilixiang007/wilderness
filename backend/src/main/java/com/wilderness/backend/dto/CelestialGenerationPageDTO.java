package com.wilderness.backend.dto;

import java.util.List;

/** 天体生成历史分页结果。 */
public record CelestialGenerationPageDTO(
		List<CelestialGenerationSummaryDTO> items,
		long totalElements,
		int totalPages,
		int page,
		int size) {
}
