package com.wilderness.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 本地转存的 AI 生成图片资产（文生图升级通道产出，DashScope 原链接约 24 小时过期，
 * 下载转存后长期有效）。这张表本身就是"这张图归属谁"的权威记录，访问接口直接查
 * ownerUserId 做鉴权，不需要反查 CelestialGenerationHistory/FavoriteGeneration。
 * 历史记录和收藏各自持有独立的一份（各自一条 GeneratedImage + 一个物理文件），
 * 删除任一方时只删自己这份，互不影响。
 */
@Entity
@Table(name = "generated_image")
public class GeneratedImage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_user_id", nullable = false)
	private Long ownerUserId;

	/** 磁盘上的绝对路径（挂载卷目录 + UUID 文件名）。 */
	@Column(name = "stored_path", nullable = false)
	private String storedPath;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected GeneratedImage() {
		// JPA
	}

	public GeneratedImage(Long ownerUserId, String storedPath, String contentType) {
		this.ownerUserId = ownerUserId;
		this.storedPath = storedPath;
		this.contentType = contentType;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getOwnerUserId() {
		return ownerUserId;
	}

	public String getStoredPath() {
		return storedPath;
	}

	public String getContentType() {
		return contentType;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
