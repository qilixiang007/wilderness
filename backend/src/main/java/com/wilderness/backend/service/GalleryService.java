package com.wilderness.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.domain.CelestialGenerationHistory;
import com.wilderness.backend.dto.GalleryItemDTO;
import com.wilderness.backend.dto.GalleryPageDTO;
import com.wilderness.backend.dto.RenderSpec;
import com.wilderness.backend.repository.CelestialGenerationHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

/** 公开广场：只读取已公开且成功的生成历史，返回"成品"字段，匿名可访问。 */
@Service
public class GalleryService {

	private final CelestialGenerationHistoryRepository repository;
	private final ObjectMapper objectMapper;

	public GalleryService(CelestialGenerationHistoryRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	public GalleryPageDTO list(int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 50);
		PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

		Page<CelestialGenerationHistory> result = repository.findByIsPublicTrueAndSuccessTrueOrderByCreatedAtDesc(pageable);
		List<GalleryItemDTO> items = result.getContent().stream().map(this::toItemDto).toList();
		return new GalleryPageDTO(items, result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
	}

	private GalleryItemDTO toItemDto(CelestialGenerationHistory history) {
		return new GalleryItemDTO(
				history.getId(), history.getName(), history.getType(), history.getIntroduction(),
				history.getImageUrl(), history.isImageTemporary(), fromJson(history.getRenderJson()),
				history.getCreatedAt());
	}

	private RenderSpec fromJson(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(json, RenderSpec.class);
		} catch (Exception e) {
			return null;
		}
	}
}
