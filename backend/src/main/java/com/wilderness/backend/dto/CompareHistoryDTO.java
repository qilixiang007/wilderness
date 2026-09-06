package com.wilderness.backend.dto;

import java.time.Instant;
import java.util.List;

/** 对比历史条目（给前端"对比历史" tab 用）。overview 为 null 表示当次综合总结失败。 */
public record CompareHistoryDTO(
		Long id,
		List<CompareItemResult> items,
		String overview,
		Instant createdAt) {
}
