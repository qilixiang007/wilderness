package com.wilderness.backend.ai.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.domain.ConversationMessage;
import com.wilderness.backend.dto.AiSource;
import com.wilderness.backend.dto.ConversationMessageEvent;
import com.wilderness.backend.mq.ConversationMqProducer;
import com.wilderness.backend.repository.ConversationMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 对话历史落库编排（尽力而为，永不抛出，绝不影响聊天响应）。
 *
 * 链路：MySQL 写入（权威存储）→ 组装事件 → 若 RocketMQ 启用则异步发送（失败回调直写 ES），
 * 否则直接写 ES 分析索引。ES 文档 _id=消息 id，重复写入幂等覆盖。
 */
@Component
@ConditionalOnProperty(name = "wilderness.history.enabled", havingValue = "true", matchIfMissing = true)
public class ConversationRecorder {

    private static final Logger log = LoggerFactory.getLogger(ConversationRecorder.class);

    private final ConversationMessageRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<ConversationMqProducer> mqProducer;
    private final ConversationEsWriter esWriter;

    public ConversationRecorder(ConversationMessageRepository repository,
                                ObjectMapper objectMapper,
                                ObjectProvider<ConversationMqProducer> mqProducer,
                                ConversationEsWriter esWriter) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.mqProducer = mqProducer;
        this.esWriter = esWriter;
    }

    public void record(Long userId, String question, boolean webEnabled, String answer, List<AiSource> sources,
                       String traceId) {
        if (userId == null) {
            return; // 未登录不落库
        }
        try {
            List<AiSource> safeSources = sources == null ? List.of() : sources;
            ConversationMessage saved = repository.save(new ConversationMessage(
                    userId, question, answer,
                    objectMapper.writeValueAsString(safeSources), webEnabled, traceId));

            ConversationMessageEvent event = new ConversationMessageEvent(
                    saved.getId(), userId, question, answer, safeSources, webEnabled, saved.getCreatedAt());

            ConversationMqProducer producer = mqProducer.getIfAvailable();
            if (producer != null) {
                producer.sendAsync(event, () -> esWriter.write(event));
            } else {
                esWriter.write(event);
            }
        } catch (Exception e) {
            log.warn("对话记录持久化失败(不影响回答): {}", e.getMessage());
        }
    }
}
