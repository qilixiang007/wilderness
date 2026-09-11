package com.wilderness.backend.ai.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 业务代码唯一依赖的追踪入口。
 *
 * - {@link #start}：线程上已有当前 span 就挂为子节点，否则开启新 trace。
 *   所以 RagService.explain 单独调用时是根，被多天体对比调用时自动成为对比 trace 的子树。
 * - {@link #child}：只在已有 trace 内建子节点，没有当前 span 时返回 NOOP，
 *   避免产生孤儿 trace（如启动时灌库触发的 embedding）。
 * - 进行中的 trace 登记在 active 表里，清扫器把超时未完成的强制收尾，防止内存泄漏。
 */
@Component
public class Tracer {

    private static final Logger log = LoggerFactory.getLogger(Tracer.class);

    private final TraceDispatcher dispatcher;
    private final boolean enabled;
    private final Duration staleTimeout;
    private final Map<String, TraceRecord> active = new ConcurrentHashMap<>();

    @Autowired
    public Tracer(TraceDispatcher dispatcher,
                  @Value("${wilderness.trace.enabled:true}") boolean enabled,
                  @Value("${wilderness.trace.stale-timeout-minutes:10}") long staleTimeoutMinutes) {
        this.dispatcher = dispatcher;
        this.enabled = enabled;
        this.staleTimeout = Duration.ofMinutes(staleTimeoutMinutes);
    }

    /** 关闭状态的 Tracer，供单测注入。 */
    public static Tracer noop() {
        return new Tracer(null, false, 10);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 有当前 span 则建子 span（userId 忽略），否则开启新 trace，userId 记在根上用于数据隔离。 */
    public Span start(String name, String runType, Map<String, Object> inputs, Long userId) {
        if (!enabled) {
            return Span.NOOP;
        }
        Span parent = TraceContextHolder.current();
        if (parent != null && !parent.isNoop()) {
            return parent.record().newChild(parent, name, runType, inputs);
        }
        TraceRecord record = new TraceRecord(userId, name, runType, inputs, this::onComplete);
        active.put(record.traceId(), record);
        return record.root();
    }

    /** 只在已有 trace 内建子 span；没有当前 span 时返回 NOOP。 */
    public Span child(String name, String runType, Map<String, Object> inputs) {
        if (!enabled) {
            return Span.NOOP;
        }
        Span parent = TraceContextHolder.current();
        if (parent == null || parent.isNoop()) {
            return Span.NOOP;
        }
        return parent.record().newChild(parent, name, runType, inputs);
    }

    /**
     * 用子 span 包裹一段代码：执行期间它是当前 span（内部的模型调用会挂在它下面），
     * 成功按 outputs 映射收尾，异常则标记失败后原样抛出。
     */
    public <T> T inChild(String name, String runType, Map<String, Object> inputs,
                         TracedCall<T> body, Function<T, Map<String, Object>> outputs) throws Exception {
        Span span = child(name, runType, inputs);
        T result;
        try (TraceScope ignored = span.makeCurrent()) {
            result = body.call();
        } catch (Exception e) {
            span.fail(e);
            throw e;
        }
        span.end(safeOutputs(outputs, result));
        return result;
    }

    /** 输出映射只服务于追踪，映射出错也不能影响业务返回值。 */
    private <T> Map<String, Object> safeOutputs(Function<T, Map<String, Object>> outputs, T result) {
        if (outputs == null) {
            return null;
        }
        try {
            return outputs.apply(result);
        } catch (Exception e) {
            return TraceAttrs.of("outputMappingError", e.toString());
        }
    }

    private void onComplete(TraceRecord record) {
        active.remove(record.traceId());
        dispatcher.dispatch(record);
    }

    /** 超时仍未完成的 trace（如 SSE 客户端断开导致回调永不触发）强制收尾后照常分发。 */
    @Scheduled(fixedDelay = 60_000)
    public void sweepStale() {
        Instant deadline = Instant.now().minus(staleTimeout);
        active.values().stream()
                .filter(record -> record.createdAt().isBefore(deadline))
                .forEach(record -> {
                    log.info("trace 超时未完成，强制收尾 traceId={} name={}", record.traceId(), record.root().name());
                    record.forceComplete();
                });
    }
}
