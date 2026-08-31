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
 * 用户账号。邮箱唯一（入库前统一小写），密码存 BCrypt 哈希。
 */
@Entity
// user 是 H2 保留字，改名 app_user（MySQL 下也避免保留字隐患）
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(name = "uk_user_email", columnNames = "email"))
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 255)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected User() {
		// JPA
	}

	public User(String email, String passwordHash) {
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.createdAt = Instant.now();
	}

	/** 邮箱归一化：去首尾空白 + 统一小写，保证唯一键对大小写不敏感。 */
	public static String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase();
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
