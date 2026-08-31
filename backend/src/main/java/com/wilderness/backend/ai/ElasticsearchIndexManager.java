package com.wilderness.backend.ai;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 知识库索引管理。
 * 索引字段:
 * - content        : 切块文本(text,用于 BM25 关键词检索)
 * - content_vector : 1024 维稠密向量(dense_vector,用于 kNN 语义检索)
 * - slug/type/zh_name/en_name/file_name : 元数据(keyword)
 * - chunk_index    : 块序号(integer)
 * - user_id        : 归属用户(keyword)。不写=公共语料(内置);写入=用户上传,检索按此隔离。
 */
@Component
public class ElasticsearchIndexManager {

    private final ElasticsearchClient es;
    private final String indexName;
    private final int embeddingDimensions;

    public ElasticsearchIndexManager(ElasticsearchClient es,
                                     @Value("${wilderness.ai.rag.index-name}") String indexName,
                                     @Value("${wilderness.ai.dashscope.embedding-dimensions}") int embeddingDimensions) {
        this.es = es;
        this.indexName = indexName;
        this.embeddingDimensions = embeddingDimensions;
    }

    public String indexName() {
        return indexName;
    }

    /** 索引不存在时创建;已存在则跳过(幂等)。 */
    public void ensureIndex() throws Exception {
        if (!es.indices().exists(e -> e.index(indexName)).value()) {
            es.indices().create(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("content", p -> p.text(t -> t))
                            .properties("content_vector", p -> p.denseVector(d -> d
                                    .dims(embeddingDimensions)
                                    .index(true)
                                    .similarity(DenseVectorSimilarity.Cosine)))
                            .properties("slug", p -> p.keyword(k -> k))
                            .properties("type", p -> p.keyword(k -> k))
                            .properties("zh_name", p -> p.keyword(k -> k))
                            .properties("en_name", p -> p.keyword(k -> k))
                            .properties("file_name", p -> p.keyword(k -> k))
                            .properties("chunk_index", p -> p.integer(i -> i))
                            .properties("user_id", p -> p.keyword(k -> k))
                    ));
        }
        // 老索引可能在加 user_id 字段前就已创建,putMapping 幂等补齐该字段
        es.indices().putMapping(m -> m
                .index(indexName)
                .properties("user_id", p -> p.keyword(k -> k)));
    }
}
