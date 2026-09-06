package com.wilderness.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 多天体对比请求。数量校验只保证原始列表长度在 2~4;
 * 去重后是否仍 ≥2 由 CompareService 兜底(如 ["earth","earth"] 应判 400)。
 */
public record CompareRequest(
        @NotEmpty(message = "请至少选择 2 个天体")
        @Size(min = 2, max = 4, message = "对比数量需在 2~4 个之间")
        List<@NotBlank String> slugs
) {
}
