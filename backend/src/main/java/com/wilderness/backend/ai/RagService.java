package com.wilderness.backend.ai;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.wilderness.backend.ai.history.ConversationRecorder;
import com.wilderness.backend.ai.trace.RunTypes;
import com.wilderness.backend.ai.trace.Span;
import com.wilderness.backend.ai.trace.TraceAttrs;
import com.wilderness.backend.ai.trace.TraceScope;
import com.wilderness.backend.ai.trace.Tracer;
import com.wilderness.backend.domain.CelestialObject;
import com.wilderness.backend.dto.AiSource;
import com.wilderness.backend.dto.ChatResponse;
import com.wilderness.backend.dto.ExplainResponse;
import com.wilderness.backend.repository.CelestialObjectRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * RAG 问答服务。
 * - chat   : 问题 → 混合检索得引文 → 组装资料 → AI 作答,返回 回答 + 引文来源;
 * - explain: 按 slug 取该天体的全部语料 → AI 写科普讲解。
 *
 * 引文来源透明返回给前端,这是 RAG 可解释性的体现。
 */
@Service
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int EXCERPT_LEN = 120;

    private final HybridContentRetriever retriever;
    private final AiAssistant assistant;
    private final ElasticsearchClient es;
    private final WebSearchService webSearchService;
    private final Tracer tracer;
    private final String indexName;
    private final ObjectProvider<ConversationRecorder> recorderProvider;
    private final CelestialObjectRepository celestialObjectRepository;

    public RagService(HybridContentRetriever retriever,
                      AiAssistant assistant,
                      ElasticsearchClient es,
                      ElasticsearchIndexManager indexManager,
                      WebSearchService webSearchService,
                      Tracer tracer,
                      ObjectProvider<ConversationRecorder> recorderProvider,
                      CelestialObjectRepository celestialObjectRepository) {
        this.retriever = retriever;
        this.assistant = assistant;
        this.es = es;
        this.webSearchService = webSearchService;
        this.tracer = tracer;
        this.indexName = indexManager.indexName();
        this.recorderProvider = recorderProvider;
        this.celestialObjectRepository = celestialObjectRepository;
    }

    public ChatResponse chat(String question, boolean webSearchEnabled, Long userId) throws Exception {
        Span root = tracer.start("rag.chat", RunTypes.CHAIN,
                TraceAttrs.of("question", question, "webSearchEnabled", webSearchEnabled), userId);
        try (TraceScope ignored = root.makeCurrent()) {
            List<Content> contents;
            boolean retrievalDegraded;
            try {
                contents = retrieveWithWeb(question, webSearchEnabled, userId);
                retrievalDegraded = false;
            } catch (Exception e) {
                log.warn("检索失败，降级为无知识库上下文回答 question={}", question, e);
                contents = List.of();
                retrievalDegraded = true;
            }
            List<AiSource> sources = toSources(contents);
            String answer = assistant.chat(today(), question, formatSources(contents));
            recordConversation(userId, question, webSearchEnabled, answer, sources, root.traceId());
            ChatResponse response = new ChatResponse(answer, sources, retrievalDegraded);
            root.end(TraceAttrs.of("answer", answer, "sourceCount", sources.size(), "retrievalDegraded", retrievalDegraded));
            return response;
        } catch (Exception e) {
            root.fail(e);
            throw e;
        }
    }

    /**
     * 流式问答。检索完成后先回调 onRetrievalStatus(true=检索失败已降级)，再回调 onSources
     * 返回引文,随后 onDelta 逐段推送回答内容,全部完成后回调 onDone,出错时回调 onError。
     * 全程不阻塞调用线程。
     */
    public void streamChat(String question,
                           boolean webSearchEnabled,
                           Long userId,
                           Consumer<String> onDelta,
                           Consumer<List<AiSource>> onSources,
                           Consumer<Boolean> onRetrievalStatus,
                           Consumer<Throwable> onError,
                           Runnable onDone) {
        Span root = tracer.start("rag.stream_chat", RunTypes.CHAIN,
                TraceAttrs.of("question", question, "webSearchEnabled", webSearchEnabled), userId);
        // 流式回调跑在模型的 HTTP 线程上，ThreadLocal 不可用：回调里直接用闭包持有的 root
        try (TraceScope ignored = root.makeCurrent()) {
            List<Content> contents;
            boolean retrievalDegraded;
            try {
                contents = retrieveWithWeb(question, webSearchEnabled, userId);
                retrievalDegraded = false;
                onRetrievalStatus.accept(false);
            } catch (Exception e) {
                log.warn("检索失败，降级为无知识库上下文回答 question={}", question, e);
                contents = List.of();
                retrievalDegraded = true;
                onRetrievalStatus.accept(true);
            }
            List<AiSource> sources = toSources(contents);
            onSources.accept(sources);
            boolean degraded = retrievalDegraded;
            // start() 在当前线程同步触发模型 listener 的 onRequest，llm span 因此挂在 root 下
            assistant.chatStream(today(), question, formatSources(contents))
                    .onPartialResponse(delta -> {
                        root.markFirstToken();
                        onDelta.accept(delta);
                    })
                    .onCompleteResponse(r -> {
                        // 流式结束:落库完整回答后,维持原有 onDone 顺序
                        String answer = r.aiMessage().text();
                        root.end(TraceAttrs.of("answer", answer, "sourceCount", sources.size(), "retrievalDegraded", degraded));
                        recordConversation(userId, question, webSearchEnabled, answer, sources, root.traceId());
                        onDone.run();
                    })
                    .onError(e -> {
                        root.fail(e);
                        onError.accept(e);
                    })
                    .start();
        } catch (Exception e) {
            root.fail(e);
            onError.accept(e);
        }
    }

    /**
     * 本地混合检索;开启联网时把联网结果作为补充资料并入上下文。
     * 联网条目以 type=web 标记,slug 存网页 URL,前端据此渲染为外部链接。
     */
    private List<Content> retrieveWithWeb(String question, boolean webSearchEnabled, Long userId) throws Exception {
        List<Content> contents = new ArrayList<>(tracer.inChild("retrieve.hybrid", RunTypes.RETRIEVER,
                TraceAttrs.of("query", question),
                () -> retriever.retrieve(Query.from(question), userId),
                RagService::documentsOutput));
        if (webSearchEnabled) {
            List<WebResult> webResults = tracer.inChild("web.search", RunTypes.TOOL,
                    TraceAttrs.of("query", question),
                    () -> webSearchService.search(question),
                    results -> TraceAttrs.of("results", results.stream()
                            .map(r -> TraceAttrs.of("title", r.title(), "url", r.url(), "snippet", r.snippet()))
                            .toList()));
            for (WebResult r : webResults) {
                Metadata meta = new Metadata()
                        .put("zh_name", r.title())
                        .put("en_name", r.title())
                        .put("slug", r.url())
                        .put("type", "web")
                        .put("file_name", r.title());
                contents.add(Content.from(TextSegment.from(r.snippet(), meta)));
            }
        }
        return contents;
    }

    /** 单独抽成方法：docs 只在这个方法内被 forEach lambda 捕获，赋值一次，天然有效 final。 */
    private List<Map> searchExplainDocs(String slug, Long userId) throws Exception {
        SearchResponse<Map> resp = es.search(s -> s
                        .index(indexName)
                        .query(q -> q.bool(b -> b
                                .must(m -> m.term(t -> t.field("slug").value(slug)))
                                .filter(userFilter(userId))))
                        .size(50)
                        .source(so -> so.filter(f -> f.includes("content", "slug", "type", "zh_name", "en_name", "file_name", "chunk_index"))),
                Map.class);

        List<Map> docs = new ArrayList<>();
        resp.hits().hits().forEach(h -> {
            if (h.source() != null) {
                docs.add(h.source());
            }
        });
        return docs;
    }

    public ExplainResponse explain(String slug, Long userId) throws Exception {
        // 单独调用时是根；被多天体对比调用时，线程上已有对比的 span，自动成为其子树
        Span root = tracer.start("rag.explain", RunTypes.CHAIN, TraceAttrs.of("slug", slug), userId);
        try (TraceScope ignored = root.makeCurrent()) {
            List<Map> docs;
            boolean retrievalDegraded;
            try {
                docs = tracer.inChild("retrieve.explain_docs", RunTypes.RETRIEVER,
                        TraceAttrs.of("slug", slug),
                        () -> searchExplainDocs(slug, userId),
                        found -> TraceAttrs.of("documents", found.stream()
                                .map(d -> TraceAttrs.of("page_content", d.get("content"), "metadata", TraceAttrs.of(
                                        "slug", d.get("slug"), "type", d.get("type"), "zh_name", d.get("zh_name"),
                                        "chunk_index", d.get("chunk_index"))))
                                .toList()));
                retrievalDegraded = false;
            } catch (Exception e) {
                log.warn("检索失败，尝试用天体目录数据降级 slug={}", slug, e);
                docs = List.of();
                retrievalDegraded = true;
            }

            if (docs.isEmpty() && !retrievalDegraded) {
                throw new IllegalArgumentException("知识库中未找到 slug=" + slug + " 的资料");
            }

            String zhName;
            String enName;
            String sourcesText;
            List<AiSource> sources;
            if (!docs.isEmpty()) {
                Map first = docs.get(0);
                zhName = str(first.get("zh_name"));
                enName = str(first.get("en_name"));
                sourcesText = docs.stream()
                        .map(d -> "- " + str(d.get("content")))
                        .reduce((a, b) -> a + "\n\n" + b)
                        .orElse("");
                sources = docs.stream()
                        .map(d -> new AiSource(str(d.get("zh_name")), str(d.get("slug")),
                                str(d.get("type")), abbreviate(str(d.get("content"))),
                                (Integer) d.get("chunk_index")))
                        .toList();
            } else {
                // ES 挂了：从跟 ES 无关的 MySQL 天体目录兜底拿名字，没有正文资料可用。
                CelestialObject object = celestialObjectRepository.findBySlug(slug)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "天体不存在: " + slug));
                zhName = object.getZhName();
                enName = object.getEnName();
                sourcesText = "";
                sources = List.of();
            }

            String answer = assistant.explain(zhName, enName, sourcesText);
            ExplainResponse response = new ExplainResponse(answer, sources, retrievalDegraded);
            root.end(TraceAttrs.of("answer", answer, "sourceCount", sources.size(), "retrievalDegraded", retrievalDegraded));
            return response;
        } catch (Exception e) {
            root.fail(e);
            throw e;
        }
    }

    /** 今天的日期,注入系统提示词作为时间锚点(时间类问题由此直接作答,不必依赖联网/资料)。 */
    private String today() {
        LocalDate now = LocalDate.now();
        String week = switch (now.getDayOfWeek()) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
        return now + " " + week;
    }

    /** 把检索结果拼成给模型的知识库资料文本。 */
    private String formatSources(List<Content> contents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contents.size(); i++) {
            TextSegment seg = contents.get(i).textSegment();
            String name = seg.metadata().getString("zh_name");
            String file = seg.metadata().getString("file_name");
            sb.append("【来源").append(i + 1).append("】")
                    .append(name != null ? name : file)
                    .append(":\n")
                    .append(seg.text())
                    .append("\n\n");
        }
        return sb.toString();
    }

    /** 检索结果转成 LangSmith retriever run 惯用的 documents 结构：[{page_content, metadata}]。 */
    private static Map<String, Object> documentsOutput(List<Content> contents) {
        return TraceAttrs.of("documents", contents.stream()
                .map(c -> {
                    TextSegment seg = c.textSegment();
                    return TraceAttrs.of("page_content", seg.text(), "metadata", seg.metadata().toMap());
                })
                .toList());
    }

    private List<AiSource> toSources(List<Content> contents) {
        return contents.stream().map(c -> {
            TextSegment seg = c.textSegment();
            return new AiSource(
                    seg.metadata().getString("zh_name"),
                    seg.metadata().getString("slug"),
                    seg.metadata().getString("type"),
                    abbreviate(seg.text()),
                    intOrNull(seg.metadata().getString("chunk_index")));
        }).toList();
    }

    /** ES 里的 chunk_index 经 Metadata 转一圈会变成字符串;联网结果等没有该字段,原样返回 null。 */
    private Integer intOrNull(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > EXCERPT_LEN ? s.substring(0, EXCERPT_LEN) + "…" : s;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 对话历史落库(尽力而为)。持久化任何失败都不应影响聊天主流程,
     * 故 recorder 空判 + try/catch 双保险。
     */
    private void recordConversation(Long userId, String question, boolean webEnabled, String answer,
                                    List<AiSource> sources, String traceId) {
        ConversationRecorder recorder = recorderProvider.getIfAvailable();
        if (recorder != null) {
            try {
                recorder.record(userId, question, webEnabled, answer, sources, traceId);
            } catch (Exception ignored) {
                // recorder 内部已自吞异常,此处兜底确保不打断回答
            }
        }
    }

    /** 与 {@link HybridContentRetriever} 相同的用户隔离过滤(此处独立实现,避免暴露内部)。 */
    private co.elastic.clients.elasticsearch._types.query_dsl.Query userFilter(Long userId) {
        if (userId == null) {
            return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.bool(b -> b
                    .mustNot(mn -> mn.exists(e -> e.field("user_id")))));
        }
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.bool(b -> b
                .should(s -> s.bool(sb -> sb.mustNot(mn -> mn.exists(e -> e.field("user_id")))))
                .should(s -> s.term(t -> t.field("user_id").value(String.valueOf(userId))))
                .minimumShouldMatch("1")));
    }
}
