package com.wilderness.backend.dto;

import java.util.List;

/** 公开广场分页结果。 */
public record GalleryPageDTO(
		List<GalleryItemDTO> items,
		long totalElements,
		int totalPages,
		int page,
		int size) {
}
