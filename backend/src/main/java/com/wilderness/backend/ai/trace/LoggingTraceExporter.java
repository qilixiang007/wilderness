package com.wilderness.backend.ai.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把完成的 trace 以缩进树的形式打到日志，便于在没有面板时直接看链路结构。
 * wilderness.trace.log-enabled=false 可关闭。
 */
@Component
@ConditionalOnProperty(name = "wilderness.trace.log-enabled", havingValue = "true", matchIfMissing = true)
public class LoggingTraceExporter implements TraceExporter {

    private static final Logger log = LoggerFactory.getLogger(LoggingTraceExporter.class);

    @Override
    public void export(TraceRecord trace) {
        if (!log.isInfoEnabled()) {
            return;
        }
        Map<String, List<Span>> children = new LinkedHashMap<>();
        for (Span span : trace.spans()) {
            if (span.parentId() != null) {
                children.computeIfAbsent(span.parentId(), k -> new ArrayList<>()).add(span);
            }
        }
        StringBuilder sb = new StringBuilder("trace 完成 traceId=").append(trace.traceId())
                .append(" userId=").append(trace.userId());
        append(sb, trace.root(), children, 0, trace.root().startTime().toEpochMilli());
        log.info(sb.toString());
    }

    private void append(StringBuilder sb, Span span, Map<String, List<Span>> children, int depth, long traceStart) {
        sb.append('\n').append("  ".repeat(depth + 1))
                .append(depth == 0 ? "" : "└ ")
                .append(span.name()).append(" [").append(span.runType()).append("] ")
                .append(span.status())
                .append(" +").append(span.startTime().toEpochMilli() - traceStart).append("ms")
                .append(" 耗时=").append(span.durationMs()).append("ms");
        if (span.totalTokens() != null) {
            sb.append(" tokens=").append(span.promptTokens()).append('/')
                    .append(span.completionTokens()).append('/').append(span.totalTokens());
        }
        if (span.modelName() != null) {
            sb.append(" model=").append(span.modelName());
        }
        if (span.firstTokenTime() != null) {
            sb.append(" TTFT=").append(span.firstTokenTime().toEpochMilli() - span.startTime().toEpochMilli()).append("ms");
        }
        for (Span child : children.getOrDefault(span.spanId(), List.of())) {
            append(sb, child, children, depth + 1, traceStart);
        }
    }
}
