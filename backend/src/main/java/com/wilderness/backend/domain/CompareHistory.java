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
 * 多天体对比历史落库（权威存储，纯 MySQL，不走 MQ/ES——对比历史量不大，也不需要全文检索分析）。
 * 对比一生成完（拿到综合总结，哪怕综合失败但至少有讲解）就自动写入一行；仅登录用户的对比落库。
 * 不单独存 slugs：itemsJson 里每个 CompareItemResult 本身就带 slug，没必要冗余存两份。
 */
@Entity
@Table(name = "compare_history",
		indexes = @Index(name = "idx_ch_user_time", columnList = "user_id, created_at"))
public class CompareHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** List<CompareItemResult> 的 JSON 序列化。 */
	@Column(name = "items_json", nullable = false, columnDefinition = "LONGTEXT")
	private String itemsJson;

	/** 综合总结；综合失败时为 null，讲解结果仍保留。 */
	@Column(columnDefinition = "LONGTEXT")
	private String overview;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected CompareHistory() {
		// JPA
	}

	public CompareHistory(Long userId, String itemsJson, String overview) {
		this.userId = userId;
		this.itemsJson = itemsJson;
		this.overview = overview;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getItemsJson() {
		return itemsJson;
	}

	public String getOverview() {
		return overview;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
