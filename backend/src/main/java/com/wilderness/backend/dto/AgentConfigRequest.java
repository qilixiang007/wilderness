package com.wilderness.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建/更新自定义智能体的请求体。 */
public record AgentConfigRequest(
		@NotBlank(message = "智能体名称不能为空")
		@Size(max = 50, message = "名称最长 50 字")
		String name,
		@NotBlank(message = "人设提示词不能为空")
		@Size(max = 4000, message = "提示词最长 4000 字")
		String systemPrompt,
		boolean knowledgeSearchEnabled,
		boolean imageGenEnabled) {
}
