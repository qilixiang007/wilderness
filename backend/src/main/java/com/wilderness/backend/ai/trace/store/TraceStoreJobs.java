package com.wilderness.backend.ai.trace.store;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.wilderness.backend.domain.AiSpan;
import com.wilderness.backend.domain.AiTrace;
import com.wilderness.backend.repository.AiSpanRepository;
import com.wilderness.backend.repository.AiTraceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 存储链路的后台任务：
 * - 启动时下发 ES 索引模板（ES 未就绪只告警，写入时会再补建）；
 * - 每分钟补偿：把结束超过 1 分钟仍未同步 ES 的 trace 从 MySQL 重建后重写 ES；
 * - 每天凌晨清理超出保留期的 MySQL 数据与 ES 日索引。
 */
@Component
public class TraceStoreJobs implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TraceStoreJobs.class);
    private static final int COMPENSATE_LIMIT = 200;
    private static final Duration COMPENSATE_DELAY = Duration.ofMinutes(1);

    private final TraceEsIndexManager indexManager;
    private final AiTraceRepository traceRepository;
    private final AiSpanRepository spanRepository;
    private final TraceSnapshotMapper mapper;
    private final TraceBatchWriter writer;
    private final TracePersistenceService persistence;
    private final ElasticsearchClient es;
    private final int retentionDays;

    public TraceStoreJobs(TraceEsIndexManager indexManager,
                          AiTraceRepository traceRepository,
                          AiSpanRepository spanRepository,
                          TraceSnapshotMapper mapper,
                          TraceBatchWriter writer,
                          TracePersistenceService persistence,
                          ElasticsearchClient es,
                          @Value("${wilderness.trace.store.retention-days:7}") int retentionDays) {
        this.indexManager = indexManager;
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
        this.mapper = mapper;
        this.writer = writer;
        this.persistence = persistence;
        this.es = es;
        this.retentionDays = retentionDays;
    }

    @Override
    public void run(String... args) {
        try {
            indexManager.ensureTemplate();
        } catch (Exception e) {
            log.warn("trace 索引模板下发失败（首次写入时会重试）: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void compensateEsSync() {
        List<AiTrace> pending = traceRepository.findUnsynced(Instant.now().minus(COMPENSATE_DELAY),
                PageRequest.of(0, COMPENSATE_LIMIT));
        if (pending.isEmpty()) {
            return;
        }
        Map<String, List<AiSpan>> spansByTrace = spanRepository
                .findByTraceIdInOrderByStartTimeAsc(pending.stream().map(AiTrace::getTraceId).toList())
                .stream()
                .collect(Collectors.groupingBy(AiSpan::getTraceId));
        List<TraceSnapshot> snapshots = pending.stream()
                .map(t -> mapper.fromEntities(t, spansByTrace.getOrDefault(t.getTraceId(), List.of())))
                .toList();
        writer.syncToEs(snapshots);
        log.info("trace ES 补偿：本轮处理 {} 条", snapshots.size());
    }

    @Scheduled(cron = "${wilderness.trace.store.retention-cron:0 30 3 * * *}")
    public void purgeExpired() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        try {
            int[] deleted = persistence.deleteStartedBefore(cutoff);
            log.info("trace 保留期清理：MySQL 删除 trace {} 条、span {} 条", deleted[0], deleted[1]);
        } catch (Exception e) {
            log.warn("trace MySQL 保留期清理失败: {}", e.getMessage());
        }
        LocalDate cutoffDate = LocalDate.ofInstant(cutoff, ZoneOffset.UTC);
        try {
            List<String> expired = es.indices().get(g -> g.index(indexManager.indexPattern())).result().keySet()
                    .stream()
                    .filter(name -> {
                        LocalDate date = indexManager.dateOf(name);
                        return date != null && date.isBefore(cutoffDate);
                    })
                    .toList();
            if (!expired.isEmpty()) {
                es.indices().delete(d -> d.index(expired));
                log.info("trace 保留期清理：删除 ES 索引 {}", expired);
            }
        } catch (Exception e) {
            log.warn("trace ES 保留期清理失败: {}", e.getMessage());
        }
    }
}
