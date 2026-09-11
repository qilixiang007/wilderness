package com.wilderness.backend.ai.trace.store;

import com.wilderness.backend.domain.AiSpan;
import com.wilderness.backend.domain.AiTrace;
import com.wilderness.backend.repository.AiSpanRepository;
import com.wilderness.backend.repository.AiTraceRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** trace/span 的 MySQL 读写（权威存储）。 */
@Service
public class TracePersistenceService {

    /** 仅作用于本次会话的 JDBC 批大小，不影响其他业务的 Hibernate 配置。 */
    private static final int JDBC_BATCH_SIZE = 50;

    private final AiTraceRepository traceRepository;
    private final AiSpanRepository spanRepository;
    private final TraceSnapshotMapper mapper;
    private final EntityManager entityManager;

    public TracePersistenceService(AiTraceRepository traceRepository,
                                   AiSpanRepository spanRepository,
                                   TraceSnapshotMapper mapper,
                                   EntityManager entityManager) {
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    /**
     * 同一事务写入一批 trace 及其全部 span。
     * 先按主键查一次已存在的 id 去重（MQ 至少一次投递、补偿重放都可能重复），保证幂等。
     *
     * @return 本次真正写入的快照（已存在的被跳过）
     */
    @Transactional
    public List<TraceSnapshot> saveBatch(List<TraceSnapshot> batch) {
        Map<String, TraceSnapshot> unique = new LinkedHashMap<>();
        for (TraceSnapshot snapshot : batch) {
            unique.putIfAbsent(snapshot.traceId(), snapshot);
        }
        Set<String> existing = new HashSet<>(traceRepository.findExistingIds(unique.keySet()));
        existing.forEach(unique::remove);
        if (unique.isEmpty()) {
            return List.of();
        }

        entityManager.unwrap(Session.class).setJdbcBatchSize(JDBC_BATCH_SIZE);
        List<AiTrace> traces = new ArrayList<>();
        List<AiSpan> spans = new ArrayList<>();
        for (TraceSnapshot snapshot : unique.values()) {
            traces.add(mapper.toTraceEntity(snapshot));
            for (SpanSnapshot span : snapshot.spans()) {
                spans.add(mapper.toSpanEntity(snapshot.traceId(), span));
            }
        }
        traceRepository.saveAll(traces);
        spanRepository.saveAll(spans);
        return List.copyOf(unique.values());
    }

    @Transactional
    public void markSynced(Collection<String> traceIds) {
        if (!traceIds.isEmpty()) {
            traceRepository.markSynced(traceIds);
        }
    }

    /** 保留期清理：先删 span 再删 trace。 */
    @Transactional
    public int[] deleteStartedBefore(Instant cutoff) {
        int spans = spanRepository.deleteStartedBefore(cutoff);
        int traces = traceRepository.deleteStartedBefore(cutoff);
        return new int[] {traces, spans};
    }
}
