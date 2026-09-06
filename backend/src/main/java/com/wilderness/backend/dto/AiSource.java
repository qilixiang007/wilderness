package com.wilderness.backend.dto;

/**
 * 引文来源信息,用于前端展示"回答依据了哪些资料"。
 * chunkIndex:该来源在原文档里的块序号(0 开始);同一 slug 命中多个分块时,
 * 前端用它标出"第几段"。联网搜索等没有分块概念的来源该字段为 null。
 */
public record AiSource(String title, String slug, String type, String excerpt, Integer chunkIndex) {
}
