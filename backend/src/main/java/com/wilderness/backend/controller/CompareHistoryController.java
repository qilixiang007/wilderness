package com.wilderness.backend.controller;

import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.CompareHistoryPageDTO;
import com.wilderness.backend.service.CompareHistoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「对比历史」接口：当前登录用户自己的多天体对比记录（分页 / 删除）。
 * 落在 /api/history/** 通配符下，AuthInterceptor 已强制要求登录，无需额外鉴权配置。
 */
@RestController
@RequestMapping("/api/history/compare")
public class CompareHistoryController {

	private final CompareHistoryService compareHistoryService;

	public CompareHistoryController(CompareHistoryService compareHistoryService) {
		this.compareHistoryService = compareHistoryService;
	}

	@GetMapping
	public ApiResponse<CompareHistoryPageDTO> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ApiResponse.ok(compareHistoryService.list(AuthContext.currentUserId(), page, size));
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@PathVariable Long id) {
		compareHistoryService.delete(AuthContext.currentUserId(), id);
		return ApiResponse.ok("已删除", null);
	}
}
