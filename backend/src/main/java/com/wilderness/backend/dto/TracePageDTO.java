package com.wilderness.backend.dto;

import java.util.List;

/**
 * trace 分页结果。
 * keywordTruncated=true 表示关键词命中的 trace 超过候选上限，只在最近的一部分里分页。
 */
public record TracePageDTO(
		List<TraceSummaryDTO> items,
		long totalElements,
		int totalPages,
		int page,
		int size,
		boolean keywordTruncated) {
}
