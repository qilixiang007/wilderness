package com.wilderness.backend.dto;

/**
 * 引文来源信息,用于前端展示"回答依据了哪些资料"。
 */
public record AiSource(String title, String slug, String type, String excerpt) {
}
