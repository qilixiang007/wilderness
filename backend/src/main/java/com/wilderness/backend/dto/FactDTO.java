package com.wilderness.backend.dto;

/**
 * 一条双语数据卡片。
 */
public record FactDTO(
		int sortOrder,
		String zhLabel,
		String enLabel,
		String zhValue,
		String enValue) {
}
