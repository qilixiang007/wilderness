package com.wilderness.backend.controller;

import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.ConversationPageDTO;
import com.wilderness.backend.service.ConversationHistoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「对话历史」接口：当前登录用户自己的问答记录（分页 / 关键词过滤 / 删除）。
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final ConversationHistoryService historyService;

    public HistoryController(ConversationHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public ApiResponse<ConversationPageDTO> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q) {
        return ApiResponse.ok(historyService.list(AuthContext.currentUserId(), q, page, size));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        historyService.delete(AuthContext.currentUserId(), id);
        return ApiResponse.ok("已删除", null);
    }
}
