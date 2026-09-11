package com.wilderness.backend.ai.trace.store;

import java.util.List;

/** 一棵完成的 trace 的不可变快照：摘要字段 + 全部 span。 */
public record TraceSnapshot(
        String traceId,
        String name,
        Long userId,
        String status,
        long startMs,
        Long endMs,
        Long durationMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        Long firstTokenMs,
        String errorMessage,
        List<SpanSnapshot> spans) {

    long approxBytes() {
        long bytes = 256L;
        for (SpanSnapshot span : spans) {
            bytes += span.approxBytes();
        }
        return bytes;
    }
}
