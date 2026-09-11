package com.wilderness.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * 一棵 AI 调用链路的摘要（每个 trace 一行），列表页直接查这张表，不必扫 span。
 * 主键是业务生成的 UUID，实现 Persistable 让 save 直接 INSERT，省掉 merge 前的那次 SELECT。
 */
@Entity
@Table(name = "ai_trace", indexes = {
		@Index(name = "idx_trace_user_start", columnList = "user_id, start_time"),
		@Index(name = "idx_trace_start", columnList = "start_time"),
		@Index(name = "idx_trace_es_synced", columnList = "es_synced, end_time")
})
public class AiTrace implements Persistable<String> {

	@Id
	@Column(name = "trace_id", length = 36)
	private String traceId;

	@Column(nullable = false, length = 128)
	private String name;

	/** 发起请求的用户；匿名请求为 null，只有管理员能看到。 */
	@Column(name = "user_id")
	private Long userId;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Column(name = "end_time")
	private Instant endTime;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "span_count", nullable = false)
	private int spanCount;

	@Column(name = "prompt_tokens")
	private Integer promptTokens;

	@Column(name = "completion_tokens")
	private Integer completionTokens;

	@Column(name = "total_tokens")
	private Integer totalTokens;

	/** 流式请求的首 token 延迟（TTFT），非流式为 null。 */
	@Column(name = "first_token_ms")
	private Long firstTokenMs;

	/** 根 span 的错误摘要（首行），完整堆栈在 span 表。 */
	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	/** ES 分析索引是否已写入；失败保持 false，由补偿任务重试。 */
	@Column(name = "es_synced", nullable = false)
	private boolean esSynced;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Transient
	private boolean isNew = true;

	protected AiTrace() {
		// JPA
	}

	public AiTrace(String traceId, String name, Long userId, String status, Instant startTime, Instant endTime,
			Long durationMs, int spanCount, Integer promptTokens, Integer completionTokens, Integer totalTokens,
			Long firstTokenMs, String errorMessage) {
		this.traceId = traceId;
		this.name = name;
		this.userId = userId;
		this.status = status;
		this.startTime = startTime;
		this.endTime = endTime;
		this.durationMs = durationMs;
		this.spanCount = spanCount;
		this.promptTokens = promptTokens;
		this.completionTokens = completionTokens;
		this.totalTokens = totalTokens;
		this.firstTokenMs = firstTokenMs;
		this.errorMessage = errorMessage;
		this.esSynced = false;
		this.createdAt = Instant.now();
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}

	@Override
	public String getId() {
		return traceId;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	public String getTraceId() {
		return traceId;
	}

	public String getName() {
		return name;
	}

	public Long getUserId() {
		return userId;
	}

	public String getStatus() {
		return status;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public Instant getEndTime() {
		return endTime;
	}

	public Long getDurationMs() {
		return durationMs;
	}

	public int getSpanCount() {
		return spanCount;
	}

	public Integer getPromptTokens() {
		return promptTokens;
	}

	public Integer getCompletionTokens() {
		return completionTokens;
	}

	public Integer getTotalTokens() {
		return totalTokens;
	}

	public Long getFirstTokenMs() {
		return firstTokenMs;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public boolean isEsSynced() {
		return esSynced;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
