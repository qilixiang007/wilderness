package com.wilderness.backend.dto;

/**
 * 文件上传入库结果。
 */
public record UploadResult(String fileName, String type, long charCount, int chunkCount, long timestamp) {
}
