package com.wilderness.backend.dto;

import java.util.Map;

/**
 * 天体生成 Agent 的 LLM 直接产出（结构化输出，AiServices 反序列化）。
 * 生成流程：解析用户描述 → 调用检索工具取真实天体参考锚点 → 结合参数与参考生成虚拟天体。
 */
public record GenerationContent(
        String name,                    // 虚拟天体中文名
        String type,                    // 中文六类：恒星/行星/卫星/星系/星云/彗星与小天体
        Map<String, String> parameters, // 参数卡片：质量/半径/表面温度/颜色 等
        String introduction,            // Markdown 科普介绍（600-900 字）
        RenderSpec render               // SVG 渲染参数
) {
}
