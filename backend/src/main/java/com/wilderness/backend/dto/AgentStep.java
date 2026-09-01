package com.wilderness.backend.dto;

/**
 * Agent 执行轨迹中的一个节点,用于前端展示"Agent 每一步做了什么"。
 * phase 取值：参数解析 / 知识库检索 / 参考锚点 / 生成 / 检索异常。
 */
public record AgentStep(String phase, String detail) {
}
