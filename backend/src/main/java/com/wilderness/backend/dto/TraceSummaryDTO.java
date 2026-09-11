package com.wilderness.backend.dto;

import java.time.Instant;

/** trace 列表条目。userEmail 仅管理员视图返回，普通用户为 null。 */
public record TraceSummaryDTO(
		String traceId,
		String name,
		String status,
		Instant startTime,
		Long durationMs,
		int spanCount,
		Integer promptTokens,
		Integer completionTokens,
		Integer totalTokens,
		Long firstTokenMs,
		String errorMessage,
		Long userId,
		String userEmail) {
}
