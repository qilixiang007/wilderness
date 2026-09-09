package com.wilderness.backend.controller;

import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.FavoriteGenerationDTO;
import com.wilderness.backend.service.FavoriteGenerationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "自建天体"收藏接口（需登录，落在 /api/favorites/** 通配符下，拦截器已保证 currentUserId 非空）。
 * PUT/DELETE 均返回最新收藏列表，前端直接用响应更新状态（照抄 FavoriteController）。
 */
@RestController
@RequestMapping("/api/favorites/generation")
public class FavoriteGenerationController {

	private final FavoriteGenerationService favoriteGenerationService;

	public FavoriteGenerationController(FavoriteGenerationService favoriteGenerationService) {
		this.favoriteGenerationService = favoriteGenerationService;
	}

	@GetMapping
	public ApiResponse<List<FavoriteGenerationDTO>> list() {
		return ApiResponse.ok(favoriteGenerationService.list(AuthContext.currentUserId()));
	}

	@PutMapping("/{generationId}")
	public ApiResponse<List<FavoriteGenerationDTO>> add(@PathVariable Long generationId) {
		return ApiResponse.ok(favoriteGenerationService.add(AuthContext.currentUserId(), generationId));
	}

	@DeleteMapping("/{generationId}")
	public ApiResponse<List<FavoriteGenerationDTO>> remove(@PathVariable Long generationId) {
		return ApiResponse.ok(favoriteGenerationService.remove(AuthContext.currentUserId(), generationId));
	}
}
