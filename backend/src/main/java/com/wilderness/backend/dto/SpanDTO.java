package com.wilderness.backend.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * trace 详情里的一个 span。inputs/outputs 以 JSON 对象返回（非字符串），前端直接格式化展示。
 * depth 为树深度（根为 0），列表已按 dotted_order 排好，即深度优先顺序，前端可直接渲染瀑布图。
 */
public record SpanDTO(
		String spanId,
		String parentSpanId,
		int depth,
		String name,
		String runType,
		String status,
		Instant startTime,
		Instant endTime,
		Long durationMs,
		String modelName,
		Integer promptTokens,
		Integer completionTokens,
		Integer totalTokens,
		Long firstTokenMs,
		JsonNode inputs,
		JsonNode outputs,
		String error) {
}
