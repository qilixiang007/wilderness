package com.wilderness.backend.ai.trace;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一个 span = trace 树上的一个节点，字段对齐 LangSmith run 格式
 * （id / trace_id / parent_run_id / dotted_order / run_type / inputs / outputs / error）。
 *
 * 线程安全要点：
 * - 结束只允许一次（CAS），流式回调、清扫器并发收尾时不会重复计数；
 * - token 用量可能被多次累加（聚合 embedding span），用 synchronized 保护。
 */
public final class Span {

    /** 追踪关闭或无父 span 时返回的占位，所有操作都是空操作。 */
    public static final Span NOOP = new Span();

    /** dotted_order 的时间段格式：UTC、微秒精度，例如 20240919T171648521691。 */
    private static final DateTimeFormatter DOTTED_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSSSSS").withZone(ZoneOffset.UTC);

    private final TraceRecord trace;
    private final String spanId;
    private final String parentId;
    private final String dottedOrder;
    private final String name;
    private final String runType;
    private final Instant startTime;
    private final Map<String, Object> inputs;
    private final AtomicBoolean ended = new AtomicBoolean();

    private volatile Instant endTime;
    private volatile Map<String, Object> outputs;
    private volatile String error;
    private volatile String status = SpanStatus.RUNNING;
    private volatile String modelName;
    private volatile Instant firstTokenTime;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    private Span() {
        this.trace = null;
        this.spanId = null;
        this.parentId = null;
        this.dottedOrder = null;
        this.name = "noop";
        this.runType = RunTypes.CHAIN;
        this.startTime = Instant.EPOCH;
        this.inputs = Collections.emptyMap();
    }

    Span(TraceRecord trace, Span parent, String name, String runType, Map<String, Object> inputs) {
        this.trace = trace;
        this.spanId = UUID.randomUUID().toString();
        this.parentId = parent == null ? null : parent.spanId;
        this.name = name;
        this.runType = runType;
        this.startTime = Instant.now();
        this.inputs = inputs == null ? Collections.emptyMap() : new LinkedHashMap<>(inputs);
        String segment = DOTTED_TIME.format(startTime) + "Z" + spanId;
        this.dottedOrder = parent == null ? segment : parent.dottedOrder + "." + segment;
    }

    public boolean isNoop() {
        return trace == null;
    }

    /** 成功收尾。重复调用只有第一次生效。 */
    public void end(Map<String, Object> outputs) {
        if (isNoop() || !ended.compareAndSet(false, true)) {
            return;
        }
        this.outputs = outputs == null ? Collections.emptyMap() : new LinkedHashMap<>(outputs);
        this.status = SpanStatus.SUCCESS;
        this.endTime = Instant.now();
        trace.onSpanEnded(this);
    }

    /** 失败收尾，记录完整异常堆栈（详情页展示用）。 */
    public void fail(Throwable error) {
        if (isNoop() || !ended.compareAndSet(false, true)) {
            return;
        }
        this.error = stackTrace(error);
        this.status = SpanStatus.ERROR;
        this.endTime = Instant.now();
        trace.onSpanEnded(this);
    }

    /** 清扫器强制收尾：不回调 trace（由 TraceRecord 自己负责分发）。 */
    boolean cancel() {
        if (isNoop() || !ended.compareAndSet(false, true)) {
            return false;
        }
        this.status = SpanStatus.CANCELLED;
        this.error = "trace 超时未结束，已被强制收尾";
        this.endTime = Instant.now();
        return true;
    }

    /** 把当前线程的「当前 span」切到自己，try-with-resources 结束时自动恢复。 */
    public TraceScope makeCurrent() {
        if (isNoop()) {
            return TraceScope.NOOP;
        }
        Span previous = TraceContextHolder.swap(this);
        return () -> TraceContextHolder.swap(previous);
    }

    /** 流式输出的首个 token 到达时间（只记第一次），用于计算 TTFT。 */
    public void markFirstToken() {
        if (!isNoop() && firstTokenTime == null) {
            firstTokenTime = Instant.now();
        }
    }

    /** 覆盖式写入一次模型调用的 token 用量。 */
    public synchronized void setUsage(Integer prompt, Integer completion, Integer total) {
        this.promptTokens = prompt;
        this.completionTokens = completion;
        this.totalTokens = total;
    }

    /** 累加 token 用量：一个聚合 span 下有多次模型调用时使用（如分批 embedding）。 */
    public synchronized void addUsage(Integer prompt, Integer completion, Integer total) {
        this.promptTokens = sum(this.promptTokens, prompt);
        this.completionTokens = sum(this.completionTokens, completion);
        this.totalTokens = sum(this.totalTokens, total);
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    TraceRecord record() {
        return trace;
    }

    public String traceId() {
        return trace == null ? null : trace.traceId();
    }

    public String spanId() {
        return spanId;
    }

    public String parentId() {
        return parentId;
    }

    public String dottedOrder() {
        return dottedOrder;
    }

    public String name() {
        return name;
    }

    public String runType() {
        return runType;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant endTime() {
        return endTime;
    }

    public Map<String, Object> inputs() {
        return inputs;
    }

    public Map<String, Object> outputs() {
        return outputs;
    }

    public String error() {
        return error;
    }

    public String status() {
        return status;
    }

    public String modelName() {
        return modelName;
    }

    public Instant firstTokenTime() {
        return firstTokenTime;
    }

    public synchronized Integer promptTokens() {
        return promptTokens;
    }

    public synchronized Integer completionTokens() {
        return completionTokens;
    }

    public synchronized Integer totalTokens() {
        return totalTokens;
    }

    public boolean isEnded() {
        return ended.get();
    }

    public Long durationMs() {
        Instant end = endTime;
        return end == null ? null : end.toEpochMilli() - startTime.toEpochMilli();
    }

    private static Integer sum(Integer a, Integer b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + b;
    }

    private static String stackTrace(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
