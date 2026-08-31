package com.wilderness.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 用户上传的知识文件清单（用于「我的文件」列表 + 删除）。
 * 实际知识内容存 Elasticsearch；这里只记录"哪个用户上传过哪个文件"。
 * 同用户同名文件复用同一行（uk_kd_user_file），保证列表一行一文件。
 */
@Entity
@Table(name = "knowledge_document",
		indexes = @Index(name = "idx_kd_user", columnList = "user_id"),
		uniqueConstraints = @UniqueConstraint(name = "uk_kd_user_file", columnNames = { "user_id", "file_name" }))
public class KnowledgeDocument {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "file_name", nullable = false, length = 255)
	private String fileName;

	@Column(length = 128)
	private String slug;

	@Column(length = 16)
	private String type;

	@Column(name = "char_count")
	private long charCount;

	@Column(name = "chunk_count")
	private int chunkCount;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt;

	protected KnowledgeDocument() {
		// JPA
	}

	public KnowledgeDocument(Long userId, String fileName, String slug, String type,
			long charCount, int chunkCount) {
		this.userId = userId;
		this.fileName = fileName;
		this.slug = slug;
		this.type = type;
		this.charCount = charCount;
		this.chunkCount = chunkCount;
		this.uploadedAt = Instant.now();
	}

	/** 同名文件重传时刷新统计与上传时间（复用同一行）。 */
	public void refresh(long charCount, int chunkCount) {
		this.charCount = charCount;
		this.chunkCount = chunkCount;
		this.uploadedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getFileName() {
		return fileName;
	}

	public String getSlug() {
		return slug;
	}

	public String getType() {
		return type;
	}

	public long getCharCount() {
		return charCount;
	}

	public int getChunkCount() {
		return chunkCount;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}
}
