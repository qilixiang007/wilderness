package com.wilderness.backend.controller;

import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.domain.GeneratedImage;
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
 * 本地转存的生成图片读取接口（需登录，落在 /api/** 通配符下，AuthInterceptor 已强制登录）。
 * 只有图片的 owner 能读——校验 GeneratedImage.ownerUserId，不是本人一律 404（不暴露存在性）。
 */
@RestController
@RequestMapping("/api/generated-images")
public class GeneratedImageController {

	private final GeneratedImageRepository repository;

	public GeneratedImageController(GeneratedImageRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/{id}")
	public ResponseEntity<byte[]> get(@PathVariable Long id) throws Exception {
		GeneratedImage image = repository.findByIdAndOwnerUserId(id, AuthContext.currentUserId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在"));
		byte[] bytes = Files.readAllBytes(Path.of(image.getStoredPath()));
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(image.getContentType()))
				.cacheControl(CacheControl.noCache().cachePrivate())
				.body(bytes);
	}
}
