package com.wilderness.backend.controller;

import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.domain.GeneratedImage;
import com.wilderness.backend.repository.CelestialGenerationHistoryRepository;
import com.wilderness.backend.repository.GeneratedImageRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地转存的生成图片读取接口（半公开，落在 /api/** 通配符下但不在 AuthInterceptor 强制登录白名单内）。
 * 两类可读场景：1) 图片挂在一条已公开的生成历史下——任何人（含未登录）可读，服务公开广场；
 * 2) 图片是私密历史的图——仅 owner 本人登录可读。其余一律 404（不暴露存在性）。
 */
@RestController
@RequestMapping("/api/generated-images")
public class GeneratedImageController {

	private final GeneratedImageRepository repository;
	private final CelestialGenerationHistoryRepository historyRepository;

	public GeneratedImageController(GeneratedImageRepository repository,
			CelestialGenerationHistoryRepository historyRepository) {
		this.repository = repository;
		this.historyRepository = historyRepository;
	}

	@GetMapping("/{id}")
	public ResponseEntity<byte[]> get(@PathVariable Long id) throws Exception {
		GeneratedImage image = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在"));
		if (!historyRepository.existsByImageIdAndIsPublicTrueAndSuccessTrue(id)) {
			Long userId = AuthContext.currentUserId();
			if (userId == null || !image.getOwnerUserId().equals(userId)) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
			}
		}
		byte[] bytes = Files.readAllBytes(Path.of(image.getStoredPath()));
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.getContentType()))
				.cacheControl(CacheControl.noCache().cachePrivate())
				.body(bytes);
	}
}
