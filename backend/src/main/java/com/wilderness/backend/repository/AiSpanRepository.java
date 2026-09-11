package com.wilderness.backend.repository;

import com.wilderness.backend.domain.AiSpan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AiSpanRepository extends JpaRepository<AiSpan, String> {

	List<AiSpan> findByTraceIdInOrderByStartTimeAsc(Collection<String> traceIds);

	/** dotted_order 各段是定长时间戳 + UUID，按字符串升序即为深度优先顺序。 */
	List<AiSpan> findByTraceIdOrderByDottedOrderAsc(String traceId);

	@Modifying
	@Query("delete from AiSpan s where s.startTime < :cutoff")
	int deleteStartedBefore(@Param("cutoff") Instant cutoff);
}
