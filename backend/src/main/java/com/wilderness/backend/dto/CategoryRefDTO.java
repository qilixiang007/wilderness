package com.wilderness.backend.dto;

/**
 * 对象详情里的所属分类引用（用于回链/面包屑）。
 */
public record CategoryRefDTO(
		String slug,
		String zhName,
		String enName) {
}
