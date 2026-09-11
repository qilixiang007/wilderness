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
 * trace 树上的一个节点，字段对齐 LangSmith run 格式。
 * 输入输出按约定不截断，存 LONGTEXT；保留期由定时任务清理。
 */
@Entity
@Table(name = "ai_span", indexes = {
		@Index(name = "idx_span_trace", columnList = "trace_id"),
		@Index(name = "idx_span_start", columnList = "start_time")
})
public class AiSpan implements Persistable<String> {

	@Id
	@Column(name = "span_id", length = 36)
	private String spanId;

	@Column(name = "trace_id", nullable = false, length = 36)
	private String traceId;

	@Column(name = "parent_span_id", length = 36)
	private String parentSpanId;

	@Column(name = "dotted_order", nullable = false, length = 1024)
	private String dottedOrder;

	@Column(nullable = false, length = 128)
	private String name;

	@Column(name = "run_type", nullable = false, length = 16)
	private String runType;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Column(name = "end_time")
	private Instant endTime;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "model_name", length = 128)
	private String modelName;

	@Column(name = "prompt_tokens")
	private Integer promptTokens;

	@Column(name = "completion_tokens")
	private Integer completionTokens;

	@Column(name = "total_tokens")
	private Integer totalTokens;

	@Column(name = "first_token_ms")
	private Long firstTokenMs;

	@Column(name = "inputs_json", columnDefinition = "LONGTEXT")
	private String inputsJson;

	@Column(name = "outputs_json", columnDefinition = "LONGTEXT")
	private String outputsJson;

	@Column(columnDefinition = "LONGTEXT")
	private String error;

	@Transient
	private boolean isNew = true;

	protected AiSpan() {
		// JPA
	}

	public AiSpan(String spanId, String traceId, String parentSpanId, String dottedOrder, String name, String runType,
			String status, Instant startTime, Instant endTime, Long durationMs, String modelName, Integer promptTokens,
			Integer completionTokens, Integer totalTokens, Long firstTokenMs, String inputsJson, String outputsJson,
			String error) {
		this.spanId = spanId;
		this.traceId = traceId;
		this.parentSpanId = parentSpanId;
		this.dottedOrder = dottedOrder;
		this.name = name;
		this.runType = runType;
		this.status = status;
		this.startTime = startTime;
		this.endTime = endTime;
		this.durationMs = durationMs;
		this.modelName = modelName;
		this.promptTokens = promptTokens;
		this.completionTokens = completionTokens;
		this.totalTokens = totalTokens;
		this.firstTokenMs = firstTokenMs;
		this.inputsJson = inputsJson;
		this.outputsJson = outputsJson;
		this.error = error;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}

	@Override
	public String getId() {
		return spanId;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	public String getSpanId() {
		return spanId;
	}

	public String getTraceId() {
		return traceId;
	}

	public String getParentSpanId() {
		return parentSpanId;
	}

	public String getDottedOrder() {
		return dottedOrder;
	}

	public String getName() {
		return name;
	}

	public String getRunType() {
		return runType;
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

	public String getModelName() {
		return modelName;
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

	public String getInputsJson() {
		return inputsJson;
	}

	public String getOutputsJson() {
		return outputsJson;
	}

	public String getError() {
		return error;
	}
}
