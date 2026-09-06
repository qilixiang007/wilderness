package com.wilderness.backend.dto;

import java.util.List;

/**
 * 「我的文件」预览:按 chunk_index 顺序返回该文件在知识库中的全部分块文本。
 */
public record KnowledgeFilePreviewDTO(String fileName, String type,
        long charCount, int chunkCount, List<String> chunks) {
}
