package com.wilderness.backend.dto;

import java.util.List;

/** 对比历史分页结果。 */
public record CompareHistoryPageDTO(
		List<CompareHistoryDTO> items,
		long totalElements,
		int totalPages,
		int page,
		int size) {
}
