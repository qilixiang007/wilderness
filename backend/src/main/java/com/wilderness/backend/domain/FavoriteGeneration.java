package com.wilderness.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 用户收藏的「自建天体」快照（照抄 {@link Favorite} 的模式）。
 * 存快照而非引用 generationId：收藏与历史记录相互独立——历史里删了不影响收藏，
 * 收藏取消也不影响历史；且收藏只关心"这个创作结果"，不带 CelestialGenerationHistory
 * 里的完整链路日志（prompt/原始返回等），那是历史模块的追溯职责，不是收藏的关注点。
 */
@Entity
@Table(name = "favorite_generation",
		uniqueConstraints = @UniqueConstraint(name = "uk_favgen_user_generation", columnNames = { "user_id", "generation_id" }))
public class FavoriteGeneration {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** 来源的 CelestialGenerationHistory 行 id，仅用于判重/关联，不做 join。 */
	@Column(name = "generation_id", nullable = false)
	private Long generationId;

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

	/** true=imageUrl 是会过期的外部临时链接；本地图或无图为 false（含义与 CelestialGenerationHistory 一致）。 */
	@Column(name = "image_temporary", nullable = false)
	private boolean imageTemporary;

	/** 本地持久化图对应的 GeneratedImage 行 id（收藏自己独立复制的那一份，与来源历史记录的 imageId 不同）。 */
	@Column(name = "image_id")
	private Long imageId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected FavoriteGeneration() {
		// JPA
	}

	public FavoriteGeneration(Long userId, Long generationId, String name, String type, String parametersJson,
			String introduction, String renderJson, String imageUrl, boolean imageTemporary, Long imageId) {
		this.userId = userId;
		this.generationId = generationId;
		this.name = name;
		this.type = type;
		this.parametersJson = parametersJson;
		this.introduction = introduction;
		this.renderJson = renderJson;
		this.imageUrl = imageUrl;
		this.imageTemporary = imageTemporary;
		this.imageId = imageId;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getGenerationId() {
		return generationId;
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

	public Instant getCreatedAt() {
		return createdAt;
	}
}
