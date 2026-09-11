package com.wilderness.backend.controller;

import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.GalleryPageDTO;
import com.wilderness.backend.service.GalleryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开广场：不在 AuthInterceptor 强制登录白名单内，匿名可访问。
 */
@RestController
@RequestMapping("/api/gallery")
public class GalleryController {

	private final GalleryService galleryService;

	public GalleryController(GalleryService galleryService) {
		this.galleryService = galleryService;
	}

	@GetMapping
	public ApiResponse<GalleryPageDTO> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ApiResponse.ok(galleryService.list(page, size));
	}
}
