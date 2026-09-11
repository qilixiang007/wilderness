package com.wilderness.backend.ai.trace;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * embedding 调用的追踪。两种模式：
 * - 普通：当前 span 下新建 embedding 子 span（如检索时对问题向量化）；
 * - 聚合：当前 span 本身就是 embedding 类型（知识库上传的分批向量化），不再逐批建 span，
 *   只把每批 token 累加到它上面——否则一个大文件会产生上百个子 span，瀑布图无法阅读。
 * 输出不记向量本身（无阅读价值且体积巨大），只记条数与维度。
 * 没有当前 span 时（启动灌库）什么都不做。
 */
@Component
public class TracingEmbeddingModelListener implements EmbeddingModelListener {

    private static final String SPAN_KEY = TracingEmbeddingModelListener.class.getName() + ".span";
    private static final String AGGREGATE_KEY = TracingEmbeddingModelListener.class.getName() + ".aggregate";

    private final Tracer tracer;

    public TracingEmbeddingModelListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void onRequest(EmbeddingModelRequestContext ctx) {
        Span current = TraceContextHolder.current();
        if (current != null && !current.isNoop() && RunTypes.EMBEDDING.equals(current.runType())) {
            ctx.attributes().put(AGGREGATE_KEY, current);
            return;
        }
        List<TextSegment> segments = ctx.textSegments();
        Span span = tracer.child("embedding", RunTypes.EMBEDDING, TraceAttrs.of(
                "texts", segments == null ? List.of() : segments.stream().map(TextSegment::text).toList()));
        if (!span.isNoop()) {
            ctx.attributes().put(SPAN_KEY, span);
        }
    }

    @Override
    public void onResponse(EmbeddingModelResponseContext ctx) {
        EmbeddingResponse response = ctx.embeddingResponse();
        TokenUsage usage = response == null ? null : response.tokenUsage();
        if (ctx.attributes().get(AGGREGATE_KEY) instanceof Span aggregate) {
            if (usage != null) {
                aggregate.addUsage(usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount());
            }
            if (response != null) {
                aggregate.setModelName(response.modelName());
            }
            return;
        }
        if (!(ctx.attributes().get(SPAN_KEY) instanceof Span span)) {
            return;
        }
        if (usage != null) {
            span.setUsage(usage.inputTokenCount(), usage.outputTokenCount(), usage.totalTokenCount());
        }
        int count = response == null || response.embeddings() == null ? 0 : response.embeddings().size();
        int dimension = count == 0 ? 0 : response.embeddings().get(0).dimension();
        if (response != null) {
            span.setModelName(response.modelName());
        }
        span.end(TraceAttrs.of("count", count, "dimension", dimension));
    }

    @Override
    public void onError(EmbeddingModelErrorContext ctx) {
        if (ctx.attributes().get(SPAN_KEY) instanceof Span span) {
            span.fail(ctx.error());
        }
        // 聚合模式下单批失败由包裹它的业务代码抛出异常并标记失败，这里不重复处理
    }
}
