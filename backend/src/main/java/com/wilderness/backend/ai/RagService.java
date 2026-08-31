package com.wilderness.backend.ai;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.wilderness.backend.ai.langsmith.LangSmithTracer;
import com.wilderness.backend.ai.langsmith.RunRef;
import com.wilderness.backend.dto.AiSource;
import com.wilderness.backend.dto.ChatResponse;
import com.wilderness.backend.dto.ExplainResponse;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    private static final int EXCERPT_LEN = 120;

    private final HybridContentRetriever retriever;
    private final AiAssistant assistant;
    private final ElasticsearchClient es;
    private final WebSearchService webSearchService;
    private final LangSmithTracer tracer;
    private final String indexName;

    public RagService(HybridContentRetriever retriever,
                      AiAssistant assistant,
                      ElasticsearchClient es,
                      ElasticsearchIndexManager indexManager,
                      WebSearchService webSearchService,
                      LangSmithTracer tracer) {
        this.retriever = retriever;
        this.assistant = assistant;
        this.es = es;
        this.webSearchService = webSearchService;
        this.tracer = tracer;
        this.indexName = indexManager.indexName();
    }

    public ChatResponse chat(String question, boolean webSearchEnabled) throws Exception {
        RunRef run = tracer.start("rag.chat", "chain", Map.of("question", question, "webSearchEnabled", webSearchEnabled));
        try {
            List<Content> contents = retrieveWithWeb(question, webSearchEnabled);
            List<AiSource> sources = toSources(contents);
            String answer = assistant.chat(question, formatSources(contents));
            ChatResponse response = new ChatResponse(answer, sources);
            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("answer", answer);
            outputs.put("sourceCount", sources.size());
            tracer.finish(run, outputs);
            return response;
        } catch (Exception e) {
            tracer.fail(run, e);
            throw e;
        }
    }

    /**
     * 流式问答。检索完成后回调 onSources 返回引文,随后 onDelta 逐段推送回答内容,
     * 全部完成后回调 onDone,出错时回调 onError。全程不阻塞调用线程。
     */
    public void streamChat(String question,
                           boolean webSearchEnabled,
                           Consumer<String> onDelta,
                           Consumer<List<AiSource>> onSources,
                           Consumer<Throwable> onError,
                           Runnable onDone) {
        RunRef run = tracer.start("rag.stream_chat", "chain", Map.of("question", question, "webSearchEnabled", webSearchEnabled));
        try {
            List<Content> contents = retrieveWithWeb(question, webSearchEnabled);
            List<AiSource> sources = toSources(contents);
            onSources.accept(sources);
            assistant.chatStream(question, formatSources(contents))
                    .onPartialResponse(onDelta::accept)
                    .onCompleteResponse(r -> {
                        // 流式结束:上报完整回答后,维持原有 onDone 顺序
                        Map<String, Object> outputs = new LinkedHashMap<>();
                        outputs.put("answer", r.aiMessage().text());
                        outputs.put("sourceCount", sources.size());
                        tracer.finish(run, outputs);
                        onDone.run();
                    })
                    .onError(e -> {
                        tracer.fail(run, e);
                        onError.accept(e);
                    })
                    .start();
        } catch (Exception e) {
            tracer.fail(run, e);
            onError.accept(e);
        }
    }

    /**
     * 本地混合检索;开启联网时把联网结果作为补充资料并入上下文。
     * 联网条目以 type=web 标记,slug 存网页 URL,前端据此渲染为外部链接。
     */
    private List<Content> retrieveWithWeb(String question, boolean webSearchEnabled) throws Exception {
        List<Content> contents = new ArrayList<>(retriever.retrieve(Query.from(question)));
        if (webSearchEnabled) {
            for (WebResult r : webSearchService.search(question)) {
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

    public ExplainResponse explain(String slug) throws Exception {
        RunRef run = tracer.start("rag.explain", "chain", Map.of("slug", slug));
        try {
            SearchResponse<Map> resp = es.search(s -> s
                            .index(indexName)
                            .query(q -> q.term(t -> t.field("slug").value(slug)))
                            .size(50)
                            .source(so -> so.filter(f -> f.includes("content", "slug", "type", "zh_name", "en_name", "file_name"))),
                    Map.class);

            List<Map> docs = new ArrayList<>();
            resp.hits().hits().forEach(h -> {
                if (h.source() != null) {
                    docs.add(h.source());
                }
            });
            if (docs.isEmpty()) {
                throw new IllegalArgumentException("知识库中未找到 slug=" + slug + " 的资料");
            }

            Map first = docs.get(0);
            String zhName = str(first.get("zh_name"));
            String enName = str(first.get("en_name"));
            String sourcesText = docs.stream()
                    .map(d -> "- " + str(d.get("content")))
                    .reduce((a, b) -> a + "\n\n" + b)
                    .orElse("");

            String answer = assistant.explain(zhName, enName, sourcesText);
            List<AiSource> sources = docs.stream()
                    .map(d -> new AiSource(str(d.get("zh_name")), str(d.get("slug")),
                            str(d.get("type")), abbreviate(str(d.get("content")))))
                    .toList();
            ExplainResponse response = new ExplainResponse(answer, sources);
            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("answer", answer);
            outputs.put("sourceCount", sources.size());
            tracer.finish(run, outputs);
            return response;
        } catch (Exception e) {
            tracer.fail(run, e);
            throw e;
        }
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

    private List<AiSource> toSources(List<Content> contents) {
        return contents.stream().map(c -> {
            TextSegment seg = c.textSegment();
            return new AiSource(
                    seg.metadata().getString("zh_name"),
                    seg.metadata().getString("slug"),
                    seg.metadata().getString("type"),
                    abbreviate(seg.text()));
        }).toList();
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
}
