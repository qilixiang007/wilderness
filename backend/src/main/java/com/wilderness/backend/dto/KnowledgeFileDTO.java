package com.wilderness.backend.dto;

import java.time.Instant;

/**
 * 「我的文件」列表项。
 */
public record KnowledgeFileDTO(Long id, String fileName, String type,
		long charCount, int chunkCount, Instant uploadedAt) {
}
