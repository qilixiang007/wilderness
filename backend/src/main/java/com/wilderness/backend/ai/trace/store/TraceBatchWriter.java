package com.wilderness.backend.ai.trace.store;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 一批 trace 的落地流程：MySQL（权威，同事务）→ ES bulk → 成功的标记 es_synced。
 * ES 失败不回滚 MySQL：数据已安全落库，只是暂时搜不到、统计不到，由补偿任务追平（最终一致）。
 */
@Component
public class TraceBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(TraceBatchWriter.class);

    private final TracePersistenceService persistence;
    private final TraceEsWriter esWriter;
    private final Counter written;
    private final Counter mysqlFailed;
    private final Counter esSyncFailed;

    public TraceBatchWriter(TracePersistenceService persistence, TraceEsWriter esWriter, MeterRegistry registry) {
        this.persistence = persistence;
        this.esWriter = esWriter;
        this.written = Counter.builder("ai.trace.written").description("写入 MySQL 的 trace 数").register(registry);
        this.mysqlFailed = Counter.builder("ai.trace.mysql.failed").description("MySQL 写入失败而丢弃的 trace 数").register(registry);
        this.esSyncFailed = Counter.builder("ai.trace.es.sync.failed").description("写 ES 失败待补偿的 trace 数").register(registry);
    }

    /** 内存队列路径：失败只记日志与指标，不抛出（后台线程不能被异常打断）。 */
    public void write(List<TraceSnapshot> batch) {
        try {
            writeOrThrow(batch);
        } catch (Exception e) {
            mysqlFailed.increment(batch.size());
            log.warn("trace 批量写入 MySQL 失败，丢弃 {} 条: {}", batch.size(), e.getMessage());
        }
    }

    /** MQ 消费路径：MySQL 失败直接抛出，交给 RocketMQ 重投（配合主键去重即至少一次 + 幂等）。 */
    public void writeOrThrow(List<TraceSnapshot> batch) {
        List<TraceSnapshot> saved = persistence.saveBatch(batch);
        written.increment(saved.size());
        syncToEs(saved);
    }

    /** 首写与补偿共用：写 ES，只标记全部 span 都成功的 trace。 */
    public void syncToEs(List<TraceSnapshot> traces) {
        if (traces.isEmpty()) {
            return;
        }
        Set<String> synced = esWriter.write(traces);
        if (synced.size() < traces.size()) {
            esSyncFailed.increment(traces.size() - synced.size());
        }
        try {
            persistence.markSynced(synced);
        } catch (Exception e) {
            // 标记失败只会导致补偿时重写一次 ES，文档 _id 固定，重写无副作用
            log.warn("标记 es_synced 失败: {}", e.getMessage());
        }
    }
}
