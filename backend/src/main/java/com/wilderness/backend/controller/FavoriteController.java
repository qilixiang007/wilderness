package com.wilderness.backend.controller;

import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.FavoriteDTO;
import com.wilderness.backend.service.FavoriteService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收藏接口（需登录，拦截器保证 currentUserId 非空）。
 * PUT/DELETE 均返回最新收藏列表，前端直接用响应更新状态。
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

	private final FavoriteService favoriteService;

	public FavoriteController(FavoriteService favoriteService) {
		this.favoriteService = favoriteService;
	}

	@GetMapping
	public ApiResponse<List<FavoriteDTO>> list() {
		return ApiResponse.ok(favoriteService.list(AuthContext.currentUserId()));
	}

	@PutMapping("/{slug}")
	public ApiResponse<List<FavoriteDTO>> add(@PathVariable String slug) {
		return ApiResponse.ok(favoriteService.add(AuthContext.currentUserId(), slug));
	}

	@DeleteMapping("/{slug}")
	public ApiResponse<List<FavoriteDTO>> remove(@PathVariable String slug) {
		return ApiResponse.ok(favoriteService.remove(AuthContext.currentUserId(), slug));
	}
}
