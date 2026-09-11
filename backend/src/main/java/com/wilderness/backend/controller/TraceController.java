package com.wilderness.backend.controller;

import com.wilderness.backend.auth.AdminOnly;
import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.TraceDetailDTO;
import com.wilderness.backend.dto.TracePageDTO;
import com.wilderness.backend.dto.TraceStatsDTO;
import com.wilderness.backend.service.TraceQueryService;
import com.wilderness.backend.service.TraceStatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「AI 调用链路」接口（需登录）：
 * - 列表 / 详情：管理员看全部，普通用户只看自己的（隔离在 TraceQueryService 统一处理）；
 * - 统计概览：仅管理员。
 * 时间参数均为 epoch 毫秒。
 */
@RestController
@RequestMapping("/api/traces")
public class TraceController {

    private final TraceQueryService queryService;
    private final TraceStatsService statsService;

    public TraceController(TraceQueryService queryService, TraceStatsService statsService) {
        this.queryService = queryService;
        this.statsService = statsService;
    }

    @GetMapping
    public ApiResponse<TracePageDTO> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId) {
        TraceQueryService.Criteria criteria = new TraceQueryService.Criteria(name, status, from, to, keyword, userId);
        return ApiResponse.ok(queryService.list(AuthContext.currentUserId(), criteria, page, size));
    }

    @AdminOnly
    @GetMapping("/stats")
    public ApiResponse<TraceStatsDTO> stats(
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to) {
        return ApiResponse.ok(statsService.stats(from, to));
    }

    @GetMapping("/{traceId}")
    public ApiResponse<TraceDetailDTO> detail(@PathVariable String traceId) {
        return ApiResponse.ok(queryService.detail(AuthContext.currentUserId(), traceId));
    }
}
