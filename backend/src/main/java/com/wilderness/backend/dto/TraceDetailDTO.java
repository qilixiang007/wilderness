package com.wilderness.backend.dto;

import java.util.List;

/** trace 详情：摘要 + 按深度优先排好序的全部 span。 */
public record TraceDetailDTO(
		TraceSummaryDTO summary,
		List<SpanDTO> spans) {
}
