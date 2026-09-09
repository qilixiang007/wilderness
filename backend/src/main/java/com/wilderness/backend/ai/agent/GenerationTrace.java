package com.wilderness.backend.ai.agent;

/**
 * 一次生成调用的过程数据快照（可变，包内可见）：由 doGenerate 创建，
 * generateViaDynamicPrompt 边构造 prompt / 边拿到模型原始返回边写入，
 * 使得即使后续 JSON 解析抛异常，doGenerate 的 catch 块依然能拿到完整日志用于落库。
 */
class GenerationTrace {

    String systemPrompt;
    String userPrompt;
    String rawResponse;
}
