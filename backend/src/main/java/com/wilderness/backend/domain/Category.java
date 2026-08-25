package com.wilderness.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 天体分类（恒星、行星、卫星、星系、星云、彗星与小天体）。
 * 中英双语字段同时存储，前端按当前语言选择展示。
 */
@Entity
@Table(name = "category", uniqueConstraints = @UniqueConstraint(name = "uk_category_slug", columnNames = "slug"))
public class Category {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64)
	private String slug;

	@Column(length = 100)
	private String zhName;

	@Column(length = 100)
	private String enName;

	@Column(columnDefinition = "TEXT")
	private String zhDescription;

	@Column(columnDefinition = "TEXT")
	private String enDescription;

	@Column(length = 2048)
	private String image;

	@Column(name = "image_alt_zh", length = 255)
	private String imageAltZh;

	@Column(name = "image_alt_en", length = 255)
	private String imageAltEn;

	private int sortOrder;

	protected Category() {
		// JPA
	}

	public Category(String slug, String zhName, String enName, String zhDescription,
			String enDescription, String image, String imageAltZh, String imageAltEn, int sortOrder) {
		this.slug = slug;
		this.zhName = zhName;
		this.enName = enName;
		this.zhDescription = zhDescription;
		this.enDescription = enDescription;
		this.image = image;
		this.imageAltZh = imageAltZh;
		this.imageAltEn = imageAltEn;
		this.sortOrder = sortOrder;
	}

	public Long getId() {
		return id;
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

	public String getZhDescription() {
		return zhDescription;
	}

	public String getEnDescription() {
		return enDescription;
	}

	public String getImage() {
		return image;
	}

	public String getImageAltZh() {
		return imageAltZh;
	}

	public String getImageAltEn() {
		return imageAltEn;
	}

	public int getSortOrder() {
		return sortOrder;
	}
}
