package com.wilderness.backend.mq;

import com.wilderness.backend.dto.ConversationMessageEvent;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 对话事件异步生产者（fire-and-forget）。
 * 仅当 name-server 已配置且 rocketmq.enabled=true 时创建；发送失败回退直写 ES，永不抛出。
 */
@Component
@ConditionalOnExpression("'${rocketmq.name-server:}'.trim().length() > 0 && ${wilderness.history.rocketmq.enabled:false}")
public class ConversationMqProducer {

    private static final Logger log = LoggerFactory.getLogger(ConversationMqProducer.class);

    private final RocketMQTemplate template;
    private final String topic;

    public ConversationMqProducer(RocketMQTemplate template,
                                  @Value("${wilderness.history.rocketmq.topic}") String topic) {
        this.template = template;
        this.topic = topic;
    }

    /**
     * 异步发送对话事件到 RocketMQ；broker 不可达/发送异常时执行 onFallback（直写 ES）。
     */
    public void sendAsync(ConversationMessageEvent event, Runnable onFallback) {
        try {
            template.asyncSend(topic, event, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    // 送达成功,ES 写入交由 consumer 消费
                }

                @Override
                public void onException(Throwable e) {
                    log.warn("对话事件发送 RocketMQ 失败,转直写 ES: {}", e.getMessage());
                    onFallback.run();
                }
            });
        } catch (Exception e) {
            log.warn("对话事件发送 RocketMQ 异常,转直写 ES: {}", e.getMessage());
            onFallback.run();
        }
    }
}
