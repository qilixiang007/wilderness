package com.wilderness.backend.dto;

import java.util.List;

/** 对话历史分页结果。 */
public record ConversationPageDTO(
		List<ConversationMessageDTO> items,
		long totalElements,
		int totalPages,
		int page,
		int size) {
}
