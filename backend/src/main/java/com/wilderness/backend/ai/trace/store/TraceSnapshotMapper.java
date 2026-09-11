package com.wilderness.backend.ai.trace.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.ai.trace.Span;
import com.wilderness.backend.ai.trace.TraceRecord;
import com.wilderness.backend.domain.AiSpan;
import com.wilderness.backend.domain.AiTrace;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 三种形态互转：内存中的 TraceRecord → 快照 → JPA 实体，以及补偿时 实体 → 快照。 */
@Component
public class TraceSnapshotMapper {

    private final ObjectMapper objectMapper;

    public TraceSnapshotMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TraceSnapshot fromRecord(TraceRecord record) {
        List<SpanSnapshot> spans = new ArrayList<>();
        Integer prompt = null;
        Integer completion = null;
        Integer total = null;
        for (Span span : record.spans()) {
            spans.add(toSnapshot(span));
            prompt = sum(prompt, span.promptTokens());
            completion = sum(completion, span.completionTokens());
            total = sum(total, span.totalTokens());
        }
        Span root = record.root();
        return new TraceSnapshot(
                record.traceId(),
                root.name(),
                record.userId(),
                root.status(),
                root.startTime().toEpochMilli(),
                millis(root.endTime()),
                root.durationMs(),
                prompt, completion, total,
                firstTokenMs(root),
                firstLine(root.error()),
                spans);
    }

    private SpanSnapshot toSnapshot(Span span) {
        return new SpanSnapshot(
                span.spanId(),
                span.parentId(),
                span.dottedOrder(),
                span.name(),
                span.runType(),
                span.status(),
                span.startTime().toEpochMilli(),
                millis(span.endTime()),
                span.durationMs(),
                span.modelName(),
                span.promptTokens(),
                span.completionTokens(),
                span.totalTokens(),
                firstTokenMs(span),
                toJson(span.inputs()),
                toJson(span.outputs()),
                span.error());
    }

    public AiTrace toTraceEntity(TraceSnapshot t) {
        return new AiTrace(t.traceId(), t.name(), t.userId(), t.status(), Instant.ofEpochMilli(t.startMs()),
                instant(t.endMs()), t.durationMs(), t.spans().size(), t.promptTokens(), t.completionTokens(),
                t.totalTokens(), t.firstTokenMs(), t.errorMessage());
    }

    public AiSpan toSpanEntity(String traceId, SpanSnapshot s) {
        return new AiSpan(s.spanId(), traceId, s.parentSpanId(), s.dottedOrder(), s.name(), s.runType(), s.status(),
                Instant.ofEpochMilli(s.startMs()), instant(s.endMs()), s.durationMs(), s.modelName(), s.promptTokens(),
                s.completionTokens(), s.totalTokens(), s.firstTokenMs(), s.inputsJson(), s.outputsJson(), s.error());
    }

    /** 补偿任务从 MySQL 重建快照，再写一遍 ES。 */
    public TraceSnapshot fromEntities(AiTrace t, List<AiSpan> spans) {
        List<SpanSnapshot> snapshots = spans.stream().map(s -> new SpanSnapshot(
                s.getSpanId(), s.getParentSpanId(), s.getDottedOrder(), s.getName(), s.getRunType(), s.getStatus(),
                s.getStartTime().toEpochMilli(), millis(s.getEndTime()), s.getDurationMs(), s.getModelName(),
                s.getPromptTokens(), s.getCompletionTokens(), s.getTotalTokens(), s.getFirstTokenMs(),
                s.getInputsJson(), s.getOutputsJson(), s.getError())).toList();
        return new TraceSnapshot(t.getTraceId(), t.getName(), t.getUserId(), t.getStatus(),
                t.getStartTime().toEpochMilli(), millis(t.getEndTime()), t.getDurationMs(), t.getPromptTokens(),
                t.getCompletionTokens(), t.getTotalTokens(), t.getFirstTokenMs(), t.getErrorMessage(), snapshots);
    }

    private String toJson(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            // 个别值无法序列化时不丢整条 span，只记下原因
            return "{\"serializationError\":" + quote(e.getMessage()) + "}";
        }
    }

    private String quote(String s) {
        try {
            return objectMapper.writeValueAsString(s);
        } catch (Exception e) {
            return "\"\"";
        }
    }

    private static Long firstTokenMs(Span span) {
        Instant first = span.firstTokenTime();
        return first == null ? null : first.toEpochMilli() - span.startTime().toEpochMilli();
    }

    private static String firstLine(String error) {
        if (error == null) {
            return null;
        }
        int newline = error.indexOf('\n');
        return (newline < 0 ? error : error.substring(0, newline)).trim();
    }

    private static Long millis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }

    private static Instant instant(Long millis) {
        return millis == null ? null : Instant.ofEpochMilli(millis);
    }

    private static Integer sum(Integer a, Integer b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : a + b;
    }
}
