package com.wilderness.backend.ai;

/**
 * 联网检索结果。联网增强模式下,抓取到的网页条目会作为额外资料并入 RAG 上下文。
 */
public record WebResult(String title, String url, String snippet) {
}
