package com.wilderness.backend.mq;

import com.wilderness.backend.ai.trace.store.TraceBatchWriter;
import com.wilderness.backend.ai.trace.store.TraceSnapshot;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * trace 快照消费者：落 MySQL + ES。
 * MySQL 写失败时抛出，由 RocketMQ 重投；写入前按主键去重，重投不会产生重复数据（至少一次 + 幂等）。
 * 使用独立的消费组：同一消费组内订阅关系必须一致，不能和对话消费者共用。
 */
@Component
@ConditionalOnExpression("'${rocketmq.name-server:}'.trim().length() > 0 && ${wilderness.trace.rocketmq.enabled:false}")
@RocketMQMessageListener(topic = "${wilderness.trace.rocketmq.topic}",
        consumerGroup = "${wilderness.trace.rocketmq.consumer-group}")
public class TraceMqConsumer implements RocketMQListener<TraceSnapshot> {

    private final TraceBatchWriter writer;

    public TraceMqConsumer(TraceBatchWriter writer) {
        this.writer = writer;
    }

    @Override
    public void onMessage(TraceSnapshot snapshot) {
        writer.writeOrThrow(List.of(snapshot));
    }
}
