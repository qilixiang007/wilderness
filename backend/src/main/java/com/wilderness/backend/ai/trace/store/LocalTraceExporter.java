package com.wilderness.backend.ai.trace.store;

import com.wilderness.backend.ai.trace.TraceExporter;
import com.wilderness.backend.ai.trace.TraceRecord;
import com.wilderness.backend.mq.TraceMqProducer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 本地存储出口：trace 完成时转成快照，RocketMQ 启用则发消息，否则直接进内存写入队列。
 * 运行在结束最后一个 span 的业务线程上，只做序列化和入队，不做任何 IO。
 */
@Component
@ConditionalOnProperty(name = "wilderness.trace.store.enabled", havingValue = "true", matchIfMissing = true)
public class LocalTraceExporter implements TraceExporter {

    /** RocketMQ 默认单条消息上限 4MB，留余量；超大的 trace 不走 MQ，直接进本地队列。 */
    private static final long MQ_MAX_BYTES = 3L * 1024 * 1024;

    private final TraceSnapshotMapper mapper;
    private final TraceWriteQueue queue;
    private final ObjectProvider<TraceMqProducer> mqProducer;

    public LocalTraceExporter(TraceSnapshotMapper mapper, TraceWriteQueue queue,
                              ObjectProvider<TraceMqProducer> mqProducer) {
        this.mapper = mapper;
        this.queue = queue;
        this.mqProducer = mqProducer;
    }

    @Override
    public void export(TraceRecord trace) {
        TraceSnapshot snapshot = mapper.fromRecord(trace);
        TraceMqProducer producer = mqProducer.getIfAvailable();
        if (producer != null && snapshot.approxBytes() <= MQ_MAX_BYTES) {
            producer.sendAsync(snapshot, () -> queue.offer(snapshot));
        } else {
            queue.offer(snapshot);
        }
    }
}
