package com.wilderness.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 使用某个自定义智能体生成天体的请求体。 */
public record AgentGenerateRequest(
		@NotBlank(message = "描述不能为空")
		@Size(max = 2000, message = "描述最长 2000 字")
		String description) {
}
