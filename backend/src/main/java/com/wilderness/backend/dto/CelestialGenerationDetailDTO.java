package com.wilderness.backend.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 天体生成历史详情：展示字段 + 完整链路日志（system/user prompt、检索参考资料原文、
 * 模型原始返回、LangSmith run id）。失败/降级的生成 success=false，errorMessage 非空。
 */
public record CelestialGenerationDetailDTO(
		Long id,
		String description,
		String name,
		String type,
		Map<String, String> parameters,
		String introduction,
		RenderSpec render,
		String imageUrl,
		List<AiSource> sources,
		List<AgentStep> steps,
		String agentName,
		boolean success,
		String errorMessage,
		String referenceText,
		String systemPrompt,
		String userPrompt,
		String rawModelResponse,
		String langsmithRunId,
		Instant createdAt) {
}
