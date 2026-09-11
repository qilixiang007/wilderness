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
 * 天体生成历史落库（权威存储，纯 MySQL，照抄 CompareHistory 的简单模式）。
 * 除了生成结果本身，还存了这条链路的完整日志（prompt / 检索参考资料原文 / 模型原始返回 /
 * LangSmith run id），失败/降级的生成也落库——这正是"可追溯"的价值所在。
 * 仅登录用户落库；save 尽力而为，不影响生成主流程。
 */
@Entity
@Table(name = "celestial_generation_history",
		indexes = {
				@Index(name = "idx_cgh_user_time", columnList = "user_id, created_at"),
				@Index(name = "idx_cgh_public_time", columnList = "is_public, success, created_at")
		})
public class CelestialGenerationHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** 用的自定义智能体 id；null 表示内置智能体。 */
	@Column(name = "agent_id")
	private Long agentId;

	/** 生成时的智能体名字快照，不做关联查询——智能体改名/删除后历史记录仍准确。 */
	@Column(name = "agent_name")
	private String agentName;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String description;

	@Column
	private String name;

	@Column
	private String type;

	@Column(name = "parameters_json", columnDefinition = "TEXT")
	private String parametersJson;

	@Column(columnDefinition = "LONGTEXT")
	private String introduction;

	@Column(name = "render_json", columnDefinition = "TEXT")
	private String renderJson;

	@Column(name = "image_url")
	private String imageUrl;

	/** true=imageUrl 是会过期的外部临时链接（未登录/本地转存失败）；本地图或无图为 false。 */
	@Column(name = "image_temporary", nullable = false)
	private boolean imageTemporary;

	/** 本地持久化图对应的 GeneratedImage 行 id；外部临时链接/无图为 null。
	 *  删除本条历史时据此联动删图片文件；收藏时据此 copyLocal 复制一份独立文件。 */
	@Column(name = "image_id")
	private Long imageId;

	@Column(name = "sources_json", columnDefinition = "TEXT")
	private String sourcesJson;

	@Column(name = "steps_json", columnDefinition = "TEXT")
	private String stepsJson;

	/** 服务端预检索注入的参考资料原文。 */
	@Column(name = "reference_text", columnDefinition = "LONGTEXT")
	private String referenceText;

	@Column(name = "system_prompt", columnDefinition = "LONGTEXT")
	private String systemPrompt;

	@Column(name = "user_prompt", columnDefinition = "LONGTEXT")
	private String userPrompt;

	/** 模型返回的原始 JSON 文本，解析前。 */
	@Column(name = "raw_model_response", columnDefinition = "LONGTEXT")
	private String rawModelResponse;

	/** 仅在 LangSmith 上报启用时有值，与 traceId 相同。 */
	@Column(name = "langsmith_run_id")
	private String langsmithRunId;

	/** 本次生成的调用链路 id（追踪关闭时为 null）。 */
	@Column(name = "trace_id", length = 36)
	private String traceId;

	@Column(nullable = false)
	private boolean success;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** 是否公开到广场；默认私密，仅本人或管理员可切换。 */
	@Column(name = "is_public", nullable = false)
	private boolean isPublic;

	protected CelestialGenerationHistory() {
		// JPA
	}

	public CelestialGenerationHistory(Long userId, Long agentId, String agentName, String description,
			String name, String type, String parametersJson, String introduction, String renderJson,
			String imageUrl, boolean imageTemporary, Long imageId, String sourcesJson, String stepsJson,
			String referenceText, String systemPrompt, String userPrompt, String rawModelResponse,
			String langsmithRunId, String traceId, boolean success, String errorMessage) {
		this.userId = userId;
		this.agentId = agentId;
		this.agentName = agentName;
		this.description = description;
		this.name = name;
		this.type = type;
		this.parametersJson = parametersJson;
		this.introduction = introduction;
		this.renderJson = renderJson;
		this.imageUrl = imageUrl;
		this.imageTemporary = imageTemporary;
		this.imageId = imageId;
		this.sourcesJson = sourcesJson;
		this.stepsJson = stepsJson;
		this.referenceText = referenceText;
		this.systemPrompt = systemPrompt;
		this.userPrompt = userPrompt;
		this.rawModelResponse = rawModelResponse;
		this.langsmithRunId = langsmithRunId;
		this.traceId = traceId;
		this.success = success;
		this.errorMessage = errorMessage;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getAgentId() {
		return agentId;
	}

	public String getAgentName() {
		return agentName;
	}

	public String getDescription() {
		return description;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public String getParametersJson() {
		return parametersJson;
	}

	public String getIntroduction() {
		return introduction;
	}

	public String getRenderJson() {
		return renderJson;
	}

	public String getImageUrl() {
		return imageUrl;
	}

	public boolean isImageTemporary() {
		return imageTemporary;
	}

	public Long getImageId() {
		return imageId;
	}

	public String getSourcesJson() {
		return sourcesJson;
	}

	public String getStepsJson() {
		return stepsJson;
	}

	public String getReferenceText() {
		return referenceText;
	}

	public String getSystemPrompt() {
		return systemPrompt;
	}

	public String getUserPrompt() {
		return userPrompt;
	}

	public String getRawModelResponse() {
		return rawModelResponse;
	}

	public String getLangsmithRunId() {
		return langsmithRunId;
	}

	public String getTraceId() {
		return traceId;
	}

	public boolean isSuccess() {
		return success;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public boolean isPublic() {
		return isPublic;
	}

	public void setPublic(boolean isPublic) {
		this.isPublic = isPublic;
	}
}
