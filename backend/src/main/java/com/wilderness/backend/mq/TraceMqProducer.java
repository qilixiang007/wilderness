package com.wilderness.backend.mq;

import com.wilderness.backend.ai.trace.store.TraceSnapshot;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * trace 快照异步生产者。仅当 name-server 已配置且 wilderness.trace.rocketmq.enabled=true 时创建；
 * 发送失败回退到本地写入队列，永不抛出。
 */
@Component
@ConditionalOnExpression("'${rocketmq.name-server:}'.trim().length() > 0 && ${wilderness.trace.rocketmq.enabled:false}")
public class TraceMqProducer {

    private static final Logger log = LoggerFactory.getLogger(TraceMqProducer.class);

    private final RocketMQTemplate template;
    private final String topic;

    public TraceMqProducer(RocketMQTemplate template,
                           @Value("${wilderness.trace.rocketmq.topic}") String topic) {
        this.template = template;
        this.topic = topic;
    }

    public void sendAsync(TraceSnapshot snapshot, Runnable onFallback) {
        try {
            template.asyncSend(topic, snapshot, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    // 送达成功，落库交由 consumer
                }

                @Override
                public void onException(Throwable e) {
                    log.warn("trace 发送 RocketMQ 失败，转本地写入队列: {}", e.getMessage());
                    onFallback.run();
                }
            });
        } catch (Exception e) {
            log.warn("trace 发送 RocketMQ 异常，转本地写入队列: {}", e.getMessage());
            onFallback.run();
        }
    }
}
