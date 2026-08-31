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
 * 用户收藏的天体快照（slug + 名称 + 图片）。
 * 存快照而非引用 slug：收藏列表零 join、直接对上前端渲染所需字段；
 * 天体是种子静态数据不会变更，快照过期风险可忽略。
 */
@Entity
@Table(name = "favorite",
		uniqueConstraints = @UniqueConstraint(name = "uk_favorite_user_slug", columnNames = { "user_id", "slug" }))
public class Favorite {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 64)
	private String slug;

	@Column(name = "zh_name", length = 100)
	private String zhName;

	@Column(name = "en_name", length = 100)
	private String enName;

	@Column(length = 2048)
	private String image;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Favorite() {
		// JPA
	}

	public Favorite(Long userId, String slug, String zhName, String enName, String image) {
		this.userId = userId;
		this.slug = slug;
		this.zhName = zhName;
		this.enName = enName;
		this.image = image;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getSlug() {
		return slug;
	}

	public String getZhName() {
		return zhName;
	}

	public String getEnName() {
		return enName;
	}

	public String getImage() {
		return image;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
