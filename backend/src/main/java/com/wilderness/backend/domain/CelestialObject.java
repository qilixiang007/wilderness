package com.wilderness.backend.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 具体天体（太阳、地球、月球、猎户座星云……），归属于某个 {@link Category}。
 * facts 为懒加载，仅在对象详情路径的事务内访问。
 */
@Entity
@Table(name = "celestial_object", uniqueConstraints = @UniqueConstraint(name = "uk_object_slug", columnNames = "slug"))
public class CelestialObject {

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

	private int sortOrder;

	/** 数据来源标注（可空：仅权威同步过的对象有值），如 "NASA NSSDCA"、来源页、拉取时间。 */
	@Column(length = 100)
	private String dataSource;

	@Column(length = 2048)
	private String sourceUrl;

	private Instant sourcedAt;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id")
	private Category category;

	@OneToMany(mappedBy = "object", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ObjectFact> facts = new ArrayList<>();

	protected CelestialObject() {
		// JPA
	}

	public CelestialObject(String slug, String zhName, String enName, String zhDescription,
			String enDescription, String image, int sortOrder, Category category) {
		this.slug = slug;
		this.zhName = zhName;
		this.enName = enName;
		this.zhDescription = zhDescription;
		this.enDescription = enDescription;
		this.image = image;
		this.sortOrder = sortOrder;
		this.category = category;
	}

	public void addFact(ObjectFact fact) {
		fact.setObject(this);
		this.facts.add(fact);
	}

	/** 权威数据同步落库后调用：写入来源标注与拉取时间。 */
	public void markSourced(String dataSource, String sourceUrl, Instant sourcedAt) {
		this.dataSource = dataSource;
		this.sourceUrl = sourceUrl;
		this.sourcedAt = sourcedAt;
	}

	/** 插入新对象需要腾位置时（如行星分类里补种水星/金星）重排已有对象的顺序。 */
	public void setSortOrder(int sortOrder) {
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

	public int getSortOrder() {
		return sortOrder;
	}

	public String getDataSource() {
		return dataSource;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public Instant getSourcedAt() {
		return sourcedAt;
	}

	public Category getCategory() {
		return category;
	}

	public List<ObjectFact> getFacts() {
		return facts;
	}
}
