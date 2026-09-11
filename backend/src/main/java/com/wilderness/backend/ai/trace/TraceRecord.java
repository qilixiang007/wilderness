package com.wilderness.backend.ai.trace;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 一棵 trace 树在内存中的收集器。
 *
 * 完成判定：根 span 已结束 且 所有已创建的 span 都已结束（openSpans 归零）。
 * 之所以不在根 span 结束时立刻分发：流式回调里「模型 listener 的 onResponse」与「业务 onCompleteResponse」
 * 先后顺序不保证；对比功能里单个讲解超时后仍在后台跑完。计数归零才分发，子 span 不会丢。
 * 完成后只分发一次（CAS）；之后再创建的迟到 span 直接返回 NOOP。
 */
public final class TraceRecord {

    private final Long userId;
    private final Instant createdAt = Instant.now();
    private final List<Span> spans = new CopyOnWriteArrayList<>();
    private final AtomicInteger openSpans = new AtomicInteger();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final Consumer<TraceRecord> onComplete;
    private final Span root;

    TraceRecord(Long userId, String name, String runType, Map<String, Object> inputs, Consumer<TraceRecord> onComplete) {
        this.userId = userId;
        this.onComplete = onComplete;
        this.root = register(new Span(this, null, name, runType, inputs));
    }

    Span newChild(Span parent, String name, String runType, Map<String, Object> inputs) {
        if (completed.get()) {
            return Span.NOOP;
        }
        return register(new Span(this, parent, name, runType, inputs));
    }

    private Span register(Span span) {
        openSpans.incrementAndGet();
        spans.add(span);
        return span;
    }

    void onSpanEnded(Span span) {
        if (openSpans.decrementAndGet() == 0 && root.isEnded()) {
            complete();
        }
    }

    /** 超时强制收尾：未结束的 span 标记 cancelled 后照常分发。 */
    void forceComplete() {
        for (Span span : spans) {
            span.cancel();
        }
        openSpans.set(0);
        complete();
    }

    private void complete() {
        if (completed.compareAndSet(false, true)) {
            onComplete.accept(this);
        }
    }

    public String traceId() {
        return root.spanId();
    }

    public Span root() {
        return root;
    }

    public Long userId() {
        return userId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** 按创建顺序排列的全部 span（含根）。 */
    public List<Span> spans() {
        return List.copyOf(spans);
    }

    public boolean isCompleted() {
        return completed.get();
    }
}
