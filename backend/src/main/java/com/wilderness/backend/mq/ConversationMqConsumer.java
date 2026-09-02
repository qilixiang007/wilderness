package com.wilderness.backend.mq;

import com.wilderness.backend.ai.history.ConversationEsWriter;
import com.wilderness.backend.dto.ConversationMessageEvent;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 对话事件消费者：把记录写入 ES 分析索引。
 * 事件驱动架构：ES 写入离开请求路径，producer 只发不写；MQ 未启用时由 recorder 直写兜底。
 */
@Component
@ConditionalOnExpression("'${rocketmq.name-server:}'.trim().length() > 0 && ${wilderness.history.rocketmq.enabled:false}")
@RocketMQMessageListener(topic = "${wilderness.history.rocketmq.topic}",
        consumerGroup = "${rocketmq.consumer.group}")
public class ConversationMqConsumer implements RocketMQListener<ConversationMessageEvent> {

    private final ConversationEsWriter esWriter;

    public ConversationMqConsumer(ConversationEsWriter esWriter) {
        this.esWriter = esWriter;
    }

    @Override
    public void onMessage(ConversationMessageEvent event) {
        esWriter.write(event);
    }
}
