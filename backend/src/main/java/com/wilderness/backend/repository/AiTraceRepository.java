package com.wilderness.backend.repository;

import com.wilderness.backend.domain.AiTrace;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AiTraceRepository extends JpaRepository<AiTrace, String> {

	/** 批量写入前查出已存在的 id：MQ 重投或补偿重放时据此去重，保证幂等。 */
	@Query("select t.traceId from AiTrace t where t.traceId in :ids")
	List<String> findExistingIds(@Param("ids") Collection<String> ids);

	/** 补偿任务：捞出结束已超过一段时间仍未同步到 ES 的 trace。 */
	@Query("select t from AiTrace t where t.esSynced = false and t.endTime < :before order by t.endTime asc")
	List<AiTrace> findUnsynced(@Param("before") Instant before, Pageable pageable);

	@Modifying
	@Query("update AiTrace t set t.esSynced = true where t.traceId in :ids")
	int markSynced(@Param("ids") Collection<String> ids);

	@Modifying
	@Query("delete from AiTrace t where t.startTime < :cutoff")
	int deleteStartedBefore(@Param("cutoff") Instant cutoff);
}
