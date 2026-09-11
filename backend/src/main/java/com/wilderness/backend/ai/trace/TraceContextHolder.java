package com.wilderness.backend.ai.trace;

/**
 * 线程上的「当前 span」。子 span 默认挂在它下面。
 * ThreadLocal 不跨线程：线程池靠 {@link TracingTaskDecorator} 透传，流式回调由调用方显式持有 span。
 */
public final class TraceContextHolder {

    private static final ThreadLocal<Span> CURRENT = new ThreadLocal<>();

    private TraceContextHolder() {
    }

    public static Span current() {
        return CURRENT.get();
    }

    /** 设置当前 span，返回之前的值，供调用方在 finally 里恢复。 */
    static Span swap(Span span) {
        Span previous = CURRENT.get();
        if (span == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(span);
        }
        return previous;
    }
}
