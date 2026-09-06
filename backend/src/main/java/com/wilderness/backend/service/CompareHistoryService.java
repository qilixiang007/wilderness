package com.wilderness.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.domain.CompareHistory;
import com.wilderness.backend.dto.CompareHistoryDTO;
import com.wilderness.backend.dto.CompareHistoryPageDTO;
import com.wilderness.backend.dto.CompareItemResult;
import com.wilderness.backend.repository.CompareHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 「对比历史」模块：当前用户自己的多天体对比记录（按用户维度隔离）。
 * 纯 MySQL，不走 MQ/ES；save 尽力而为、绝不影响对比主流程。
 */
@Service
public class CompareHistoryService {

	private static final Logger log = LoggerFactory.getLogger(CompareHistoryService.class);

	private final CompareHistoryRepository repository;
	private final ObjectMapper objectMapper;

	public CompareHistoryService(CompareHistoryRepository repository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.objectMapper = objectMapper;
	}

	/** 未登录（userId 为 null）不落库；序列化/落库失败只记日志，不向上抛。 */
	public void save(Long userId, List<CompareItemResult> items, String overview) {
		if (userId == null) {
			return;
		}
		try {
			String itemsJson = objectMapper.writeValueAsString(items);
			repository.save(new CompareHistory(userId, itemsJson, overview));
		} catch (Exception e) {
			log.warn("compare history save failed, userId={}", userId, e);
		}
	}

	public CompareHistoryPageDTO list(Long userId, int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 50);
		PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

		Page<CompareHistory> result = repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
		List<CompareHistoryDTO> items = result.getContent().stream().map(this::toDto).toList();
		return new CompareHistoryPageDTO(items, result.getTotalElements(), result.getTotalPages(),
				result.getNumber(), result.getSize());
	}

	@Transactional
	public void delete(Long userId, Long id) {
		CompareHistory history = repository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
		repository.delete(history);
	}

	private CompareHistoryDTO toDto(CompareHistory history) {
		List<CompareItemResult> items;
		try {
			items = objectMapper.readValue(history.getItemsJson(), new TypeReference<List<CompareItemResult>>() { });
		} catch (Exception e) {
			items = List.of();
		}
		return new CompareHistoryDTO(history.getId(), items, history.getOverview(), history.getCreatedAt());
	}
}
