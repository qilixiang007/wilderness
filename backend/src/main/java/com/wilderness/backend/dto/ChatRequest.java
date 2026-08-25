package com.wilderness.backend.dto;

/**
 * AI 问答请求。
 *
 * @param webSearchEnabled 是否开启联网增强检索(默认 false,只查本地知识库)
 */
public record ChatRequest(String question, boolean webSearchEnabled) {
}
