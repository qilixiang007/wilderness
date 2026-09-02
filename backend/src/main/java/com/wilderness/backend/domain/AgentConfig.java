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
 * 用户自定义智能体（按用户隔离）。
 * 在既有天体生成 Agent 基础上叠加：用户自定人设/风格提示词 + 工具开关
 * （知识库检索 / 文生图），生成时选用。只存储配置，不持有提示词之外的逻辑。
 */
@Entity
@Table(name = "agent_config", indexes = @Index(name = "idx_ac_user", columnList = "user_id"))
public class AgentConfig {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 50)
	private String name;

	/** 用户自定人设/风格提示词，叠加在基础天体生成提示之上。 */
	@Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
	private String systemPrompt;

	/** 是否启用知识库检索（服务端预检索真实天体作参考锚点）。 */
	@Column(name = "knowledge_search_enabled", nullable = false)
	private boolean knowledgeSearchEnabled = true;

	/** 是否启用文生图升级通道（不启用则前端 SVG 程序化渲染）。 */
	@Column(name = "image_gen_enabled", nullable = false)
	private boolean imageGenEnabled = false;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AgentConfig() {
		// JPA
	}

	public AgentConfig(Long userId, String name, String systemPrompt,
			boolean knowledgeSearchEnabled, boolean imageGenEnabled) {
		this.userId = userId;
		this.name = name;
		this.systemPrompt = systemPrompt;
		this.knowledgeSearchEnabled = knowledgeSearchEnabled;
		this.imageGenEnabled = imageGenEnabled;
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String name, String systemPrompt,
			boolean knowledgeSearchEnabled, boolean imageGenEnabled) {
		this.name = name;
		this.systemPrompt = systemPrompt;
		this.knowledgeSearchEnabled = knowledgeSearchEnabled;
		this.imageGenEnabled = imageGenEnabled;
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public String getSystemPrompt() {
		return systemPrompt;
	}

	public boolean isKnowledgeSearchEnabled() {
		return knowledgeSearchEnabled;
	}

	public boolean isImageGenEnabled() {
		return imageGenEnabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
