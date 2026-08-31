package com.wilderness.backend.ai;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合检索器(BM25 关键词 + 向量语义),实现 langchain4j 的 ContentRetriever 接口。
 *
 * 检索流程:
 * 1. 将问题向量化,得到查询向量;
 * 2. 双路独立检索:
 *    - BM25:对 content / zh_name 做 multi_match 关键词检索;
 *    - kNN :对 content_vector 做向量相似度检索;
 * 3. 分数融合:两路分数各自做 min-max 归一化,按 keyword-weight / vector-weight 加权求和;
 * 4. 过滤低于 min-score 的结果,按融合分降序取 top-k。
 *
 * 归一化 + 加权是核心:BM25 分数与向量相似度量纲不同,不能直接比较,
 * 必须先归一到同一量纲再融合。这也是"自写混合检索"的面试亮点。
 */
@Component
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class HybridContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridContentRetriever.class);

    private final ElasticsearchClient es;
    private final EmbeddingModel embeddingModel;
    private final String indexName;
    private final int topK;
    private final double keywordWeight;
    private final double vectorWeight;
    private final double minScore;

    public HybridContentRetriever(ElasticsearchClient es,
                                  EmbeddingModel embeddingModel,
                                  ElasticsearchIndexManager indexManager,
                                  @Value("${wilderness.ai.rag.top-k}") int topK,
                                  @Value("${wilderness.ai.rag.keyword-weight}") double keywordWeight,
                                  @Value("${wilderness.ai.rag.vector-weight}") double vectorWeight,
                                  @Value("${wilderness.ai.rag.min-score}") double minScore) {
        this.es = es;
        this.embeddingModel = embeddingModel;
        this.indexName = indexManager.indexName();
        this.topK = topK;
        this.keywordWeight = keywordWeight;
        this.vectorWeight = vectorWeight;
        this.minScore = minScore;
    }

    @Override
    public List<Content> retrieve(Query query) {
        return retrieve(query, null);
    }

    /**
     * 按用户过滤的检索:userId 为 null(未登录)只检公共语料;登录则检「公共 + 自己的」。
     */
    public List<Content> retrieve(Query query, Long userId) {
        String question = query.text();
        try {
            Response<Embedding> embResp = embeddingModel.embed(question);
            float[] queryVector = embResp.content().vector();

            int candidateSize = topK * 3; // 双路各多召回一些候选,保证融合召回率
            List<Hit<Map>> keywordHits = searchKeyword(question, candidateSize, userId);
            List<Hit<Map>> vectorHits = searchVector(queryVector, candidateSize, userId);
            log.info("混合检索完成:question={}, 关键词命中={}, 向量命中={}",
                    question, keywordHits.size(), vectorHits.size());

            // 合并候选集合:docId -> 文档 source;两路分数分别记录
            Map<String, Map> docsById = new LinkedHashMap<>();
            Map<String, Double> keywordScores = new HashMap<>();
            Map<String, Double> vectorScores = new HashMap<>();
            collect(keywordHits, docsById, keywordScores);
            collect(vectorHits, docsById, vectorScores);

            // min-max 归一化所需的最值
            double kwMin = min(keywordScores), kwMax = max(keywordScores);
            double vMin = min(vectorScores), vMax = max(vectorScores);

            // 加权融合 + 阈值过滤 + 排序取 top-k
            List<ScoredDoc> fused = new ArrayList<>();
            for (Map.Entry<String, Map> e : docsById.entrySet()) {
                double kw = keywordScores.getOrDefault(e.getKey(), 0.0);
                double vv = vectorScores.getOrDefault(e.getKey(), 0.0);
                double score = keywordWeight * norm(kw, kwMin, kwMax)
                        + vectorWeight * norm(vv, vMin, vMax);
                if (score >= minScore) {
                    fused.add(new ScoredDoc(e.getKey(), score, e.getValue()));
                }
            }
            fused.sort(Comparator.comparingDouble(ScoredDoc::score).reversed());
            List<ScoredDoc> top = fused.size() > topK ? fused.subList(0, topK) : fused;

            return top.stream()
                    .map(d -> Content.from(TextSegment.from(
                            (String) d.source().get("content"),
                            new Metadata()
                                    .put("slug", str(d.source().get("slug")))
                                    .put("type", str(d.source().get("type")))
                                    .put("zh_name", str(d.source().get("zh_name")))
                                    .put("en_name", str(d.source().get("en_name")))
                                    .put("file_name", str(d.source().get("file_name"))))))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("混合检索失败:" + e.getMessage(), e);
        }
    }

    /**
     * 用户过滤条件:未登录(null)只检「无 user_id 的公共语料」;
     * 登录检「公共语料 + 本人上传」。两者用 bool should 合并。
     */
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

    /** BM25 关键词检索。 */
    private List<Hit<Map>> searchKeyword(String text, int size, Long userId) throws Exception {
        SearchResponse<Map> resp = es.search(s -> s
                        .index(indexName)
                        .query(q -> q.bool(b -> b
                                .must(m -> m.multiMatch(mm -> mm
                                        .query(text)
                                        .fields("content", "zh_name")))
                                .filter(userFilter(userId))))
                        .size(size)
                        .source(so -> so.filter(f -> f.includes("content", "slug", "type", "zh_name", "en_name", "file_name"))),
                Map.class);
        return resp.hits().hits();
    }

    /** 向量 kNN 检索。 */
    private List<Hit<Map>> searchVector(float[] vector, int size, Long userId) throws Exception {
        List<Float> queryVector = new ArrayList<>();
        for (float f : vector) {
            queryVector.add(f);
        }
        SearchResponse<Map> resp = es.search(s -> s
                        .index(indexName)
                        .knn(k -> k.field("content_vector").queryVector(queryVector).k(size).numCandidates(size * 10)
                                .filter(userFilter(userId)))
                        .size(size)
                        .source(so -> so.filter(f -> f.includes("content", "slug", "type", "zh_name", "en_name", "file_name"))),
                Map.class);
        return resp.hits().hits();
    }

    private void collect(List<Hit<Map>> hits,
                         Map<String, Map> docsById,
                         Map<String, Double> scores) {
        for (Hit<Map> h : hits) {
            if (h.source() == null) {
                continue;
            }
            docsById.putIfAbsent(h.id(), h.source());
            scores.put(h.id(), h.score() == null ? 0.0 : h.score());
        }
    }

    private double min(Map<String, Double> scores) {
        return scores.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
    }

    private double max(Map<String, Double> scores) {
        return scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    /** min-max 归一化;若 max==min(单值或无值),视为 1.0 避免除零。 */
    private double norm(double x, double min, double max) {
        if (max <= min) {
            return 1.0;
        }
        return (x - min) / (max - min);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private record ScoredDoc(String id, double score, Map source) {
    }
}
