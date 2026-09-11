package com.wilderness.backend.ai.trace.store;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * span 批量写入 ES 分析索引。文档 _id = spanId，重复写入幂等覆盖（首写与补偿可以放心重放）。
 * 按 span 数分块 bulk，避免单个请求过大撞上客户端 3 秒读超时。
 * 返回本批中「全部 span 都写成功」的 traceId，只有它们会被标记 es_synced。
 */
@Component
public class TraceEsWriter {

    private static final Logger log = LoggerFactory.getLogger(TraceEsWriter.class);
    private static final int SPANS_PER_BULK = 200;

    private final ElasticsearchClient es;
    private final TraceEsIndexManager indexManager;

    public TraceEsWriter(ElasticsearchClient es, TraceEsIndexManager indexManager) {
        this.es = es;
        this.indexManager = indexManager;
    }

    public Set<String> write(List<TraceSnapshot> traces) {
        if (traces.isEmpty()) {
            return Set.of();
        }
        if (!indexManager.isTemplateReady()) {
            try {
                indexManager.ensureTemplate();
            } catch (Exception e) {
                log.warn("trace 索引模板未就位，本批暂不写 ES（等待补偿）: {}", e.getMessage());
                return Set.of();
            }
        }

        Set<String> failedTraces = new HashSet<>();
        List<BulkOperation> ops = new ArrayList<>();
        Map<String, String> traceOfSpan = new LinkedHashMap<>();
        for (TraceSnapshot trace : traces) {
            String index = indexManager.indexFor(trace.startMs());
            for (SpanSnapshot span : trace.spans()) {
                Map<String, Object> doc = toDocument(trace, span);
                ops.add(BulkOperation.of(b -> b.index(i -> i.index(index).id(span.spanId()).document(doc))));
                traceOfSpan.put(span.spanId(), trace.traceId());
                if (ops.size() >= SPANS_PER_BULK) {
                    flush(ops, traceOfSpan, failedTraces);
                }
            }
        }
        flush(ops, traceOfSpan, failedTraces);

        return traces.stream()
                .map(TraceSnapshot::traceId)
                .filter(id -> !failedTraces.contains(id))
                .collect(Collectors.toSet());
    }

    private void flush(List<BulkOperation> ops, Map<String, String> traceOfSpan, Set<String> failedTraces) {
        if (ops.isEmpty()) {
            return;
        }
        try {
            BulkResponse response = es.bulk(b -> b.operations(ops));
            if (response.errors()) {
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        failedTraces.add(traceOfSpan.get(item.id()));
                    }
                }
                log.warn("trace bulk 写入部分失败，涉及 {} 个 trace（等待补偿）", failedTraces.size());
            }
        } catch (Exception e) {
            failedTraces.addAll(traceOfSpan.values());
            log.warn("trace bulk 写入 ES 失败（等待补偿）: {}", e.getMessage());
        }
        ops.clear();
        traceOfSpan.clear();
    }

    private Map<String, Object> toDocument(TraceSnapshot trace, SpanSnapshot span) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("trace_id", trace.traceId());
        doc.put("span_id", span.spanId());
        doc.put("parent_span_id", span.parentSpanId());
        doc.put("is_root", span.parentSpanId() == null);
        doc.put("user_id", trace.userId() == null ? null : String.valueOf(trace.userId()));
        doc.put("trace_name", trace.name());
        doc.put("name", span.name());
        doc.put("run_type", span.runType());
        doc.put("status", span.status());
        doc.put("model_name", span.modelName());
        doc.put("start_time", span.startMs());
        doc.put("duration_ms", span.durationMs());
        doc.put("first_token_ms", span.firstTokenMs());
        doc.put("prompt_tokens", span.promptTokens());
        doc.put("completion_tokens", span.completionTokens());
        doc.put("total_tokens", span.totalTokens());
        doc.put("inputs", span.inputsJson());
        doc.put("outputs", span.outputsJson());
        doc.put("error", span.error());
        return doc;
    }
}
