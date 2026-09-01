package com.wilderness.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 天体生成请求：自然语言描述 + 表单拼接的文本。
 */
public record GenerateCelestialRequest(
        @NotBlank(message = "描述不能为空")
        @Size(max = 2000, message = "描述过长，请精简到 2000 字以内")
        String description
) {
}
