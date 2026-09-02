package com.wilderness.backend.ai.history;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.wilderness.backend.dto.ConversationMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 对话记录写入 ES 分析索引。
 * 文档 _id = conversation_message.id：同一对话重复写入（MQ 与直写兜底并存）为幂等覆盖，不会产生双份。
 * ES 写入为尽力而为，失败只记日志，绝不向上抛。
 */
@Component
public class ConversationEsWriter {

    private static final Logger log = LoggerFactory.getLogger(ConversationEsWriter.class);

    private final ElasticsearchClient es;
    private final ConversationEsIndexManager indexManager;

    public ConversationEsWriter(ElasticsearchClient es, ConversationEsIndexManager indexManager) {
        this.es = es;
        this.indexManager = indexManager;
    }

    /** 写入一条对话到分析索引；失败仅告警。 */
    public void write(ConversationMessageEvent event) {
        try {
            es.index(i -> i
                    .index(indexManager.indexName())
                    .id(String.valueOf(event.id()))
                    .document(Map.of(
                            "user_id", String.valueOf(event.userId()),
                            "question", event.question(),
                            "answer", event.answer(),
                            "web_enabled", event.webEnabled(),
                            "created_at", event.createdAt().toString(),
                            "sources_json", event.sources())));
        } catch (Exception e) {
            log.warn("对话历史写入 ES 失败(id={}): {}", event.id(), e.getMessage());
        }
    }

    /** 删除某条对话的分析索引文档（尽力而为）。 */
    public void delete(Long messageId) {
        try {
            es.delete(d -> d.index(indexManager.indexName()).id(String.valueOf(messageId)));
        } catch (Exception e) {
            log.warn("对话历史从 ES 删除失败(id={}): {}", messageId, e.getMessage());
        }
    }
}
