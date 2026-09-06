package com.wilderness.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 天体的一条数据卡片：中英双语的标签与取值（如"半径 / Radius · 约 6,371 千米 / about 6,371 km"）。
 * 用独立实体而非 JSON 列，保证 MySQL 与 H2 行为一致且可查询。
 */
@Entity
@Table(name = "object_fact")
public class ObjectFact {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private int sortOrder;

	@Column(name = "zh_label", length = 100)
	private String zhLabel;

	@Column(name = "en_label", length = 100)
	private String enLabel;

	@Column(name = "zh_value", length = 255)
	private String zhValue;

	@Column(name = "en_value", length = 255)
	private String enValue;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "object_id")
	private CelestialObject object;

	protected ObjectFact() {
		// JPA
	}

	public ObjectFact(int sortOrder, String zhLabel, String enLabel, String zhValue, String enValue) {
		this.sortOrder = sortOrder;
		this.zhLabel = zhLabel;
		this.enLabel = enLabel;
		this.zhValue = zhValue;
		this.enValue = enValue;
	}

	/** 权威数据覆盖时原位改值：label/排序不动，仅更新双语取值。 */
	public void updateValue(String zhValue, String enValue) {
		this.zhValue = zhValue;
		this.enValue = enValue;
	}

	public Long getId() {
		return id;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public String getZhLabel() {
		return zhLabel;
	}

	public String getEnLabel() {
		return enLabel;
	}

	public String getZhValue() {
		return zhValue;
	}

	public String getEnValue() {
		return enValue;
	}

	public CelestialObject getObject() {
		return object;
	}

	public void setObject(CelestialObject object) {
		this.object = object;
	}
}
