package com.wilderness.backend.ai.trace.store;

/**
 * span 的不可变快照：inputs/outputs 已序列化成 JSON 字符串，时间统一为 epoch 毫秒。
 * 既是写库的中间形态，也是 MQ 消息体（纯基础类型，序列化无歧义）。
 */
public record SpanSnapshot(
        String spanId,
        String parentSpanId,
        String dottedOrder,
        String name,
        String runType,
        String status,
        long startMs,
        Long endMs,
        Long durationMs,
        String modelName,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long firstTokenMs,
        String inputsJson,
        String outputsJson,
        String error) {

    /** 粗略估算内存占用（按字符数计），用于写入队列的字节预算。 */
    long approxBytes() {
        return 256L + len(dottedOrder) + len(inputsJson) + len(outputsJson) + len(error);
    }

    private static long len(String s) {
        return s == null ? 0 : s.length() * 2L;
    }
}
