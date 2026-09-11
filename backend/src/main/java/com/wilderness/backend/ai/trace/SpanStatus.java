package com.wilderness.backend.ai.trace;

/** span 状态；cancelled 表示 trace 超时仍未结束（如 SSE 客户端断开），由清扫器强制收尾。 */
public final class SpanStatus {

    public static final String RUNNING = "running";
    public static final String SUCCESS = "success";
    public static final String ERROR = "error";
    public static final String CANCELLED = "cancelled";

    private SpanStatus() {
    }
}
