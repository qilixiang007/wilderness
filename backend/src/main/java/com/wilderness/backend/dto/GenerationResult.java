package com.wilderness.backend.dto;

import java.util.List;
import java.util.Map;

/**
 * 天体生成 Agent 的完整响应：LLM 产出内容 + 可选文生图 URL + 参考来源 + Agent 执行轨迹。
 * imageUrl 为 null 时前端回退 SVG 程序化渲染（双通道的默认通道）。
 */
public record GenerationResult(
        String name,
        String type,
        Map<String, String> parameters,
        String introduction,
        RenderSpec render,
        String imageUrl,              // 文生图 URL；未配置/失败为 null → 前端走 SVG
        List<AiSource> sources,       // 检索命中的真实天体，可跳转详情页
        List<AgentStep> steps,        // Agent 执行轨迹
        Long historyId,               // 对应的 CelestialGenerationHistory 行 id；未登录/落库失败为 null，前端据此判断能否收藏
        boolean imageTemporary,       // true=imageUrl 是会过期的外部临时链接（未登录/转存失败）；本地图或无图为 false
        boolean success                // false=模型侧异常降级返回（degradedResult）；此时 render 为 null，
                                        // 前端若无 imageUrl 会用 CelestialVisual 的默认参数画占位图，不代表真实生成结果
) {
}
