package com.wilderness.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 对话历史落库（权威存储）。
 * 每次 AI 问答（非流式 / 流式均含）在回答完成后写入一行；
 * 同时该记录会异步进入 Elasticsearch（分析检索）与 RocketMQ（事件驱动下游）。
 * 仅登录用户的问答落库；未登录不记录。
 */
@Entity
@Table(name = "conversation_message",
		indexes = @Index(name = "idx_cm_user_time", columnList = "user_id, created_at"))
public class ConversationMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String question;

	@Column(nullable = false, columnDefinition = "LONGTEXT")
	private String answer;

	/** 引文来源 JSON（List<AiSource> 序列化），仅展示用。 */
	@Column(name = "sources_json", nullable = false, columnDefinition = "TEXT")
	private String sourcesJson;

	@Column(name = "web_enabled", nullable = false)
	private boolean webEnabled;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** 本次问答的调用链路 id（追踪关闭时为 null），历史页据此跳转 trace 详情。 */
	@Column(name = "trace_id", length = 36)
	private String traceId;

	protected ConversationMessage() {
		// JPA
	}

	public ConversationMessage(Long userId, String question, String answer, String sourcesJson, boolean webEnabled,
			String traceId) {
		this.userId = userId;
		this.question = question;
		this.answer = answer;
		this.sourcesJson = sourcesJson;
		this.webEnabled = webEnabled;
		this.traceId = traceId;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getQuestion() {
		return question;
	}

	public String getAnswer() {
		return answer;
	}

	public String getSourcesJson() {
		return sourcesJson;
	}

	public boolean isWebEnabled() {
		return webEnabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getTraceId() {
		return traceId;
	}
}
