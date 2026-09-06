package com.wilderness.backend.dto;

import java.util.List;

/**
 * overview 为 null 表示综合总结失败(有成功项但综合调用超时/出错),
 * 前端据此显示"综合失败,重试"而非把整个对比结果判为失败。
 */
public record CompareResult(List<CompareItemResult> items, String overview) {
}
