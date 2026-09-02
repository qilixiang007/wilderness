package com.wilderness.backend.ai.history;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 对话历史分析索引管理。
 * 与知识库索引分离，字段:
 * - user_id     : 归属用户(keyword),分析/按用户维度检索
 * - question    : 用户提问(text, BM25 关键词检索)
 * - answer      : AI 回答(text, BM25)
 * - web_enabled : 是否开启联网(boolean)
 * - created_at  : 时间(date, 时间范围检索)
 * - sources_json: 引文来源(enabled=false, 仅存储不索引,保持 _source 完整)
 * 无向量字段——对话分析以关键词/过滤为主。
 */
@Component
public class ConversationEsIndexManager {

    private final ElasticsearchClient es;
    private final String indexName;

    public ConversationEsIndexManager(ElasticsearchClient es,
                                      @Value("${wilderness.history.index-name}") String indexName) {
        this.es = es;
        this.indexName = indexName;
    }

    public String indexName() {
        return indexName;
    }

    /** 索引不存在时创建(幂等)。 */
    public void ensureIndex() throws Exception {
        if (!es.indices().exists(e -> e.index(indexName)).value()) {
            es.indices().create(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("user_id", p -> p.keyword(k -> k))
                            .properties("question", p -> p.text(t -> t))
                            .properties("answer", p -> p.text(t -> t))
                            .properties("web_enabled", p -> p.boolean_(b -> b))
                            .properties("created_at", p -> p.date(d -> d))
                            .properties("sources_json", p -> p.object(o -> o.enabled(false)))
                    ));
        }
    }
}
