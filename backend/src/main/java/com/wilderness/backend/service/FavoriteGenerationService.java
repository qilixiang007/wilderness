package com.wilderness.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.domain.CelestialGenerationHistory;
import com.wilderness.backend.domain.FavoriteGeneration;
import com.wilderness.backend.dto.FavoriteGenerationDTO;
import com.wilderness.backend.dto.RenderSpec;
import com.wilderness.backend.repository.CelestialGenerationHistoryRepository;
import com.wilderness.backend.repository.FavoriteGenerationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 「自建天体」收藏业务：按用户隔离。收藏存生成结果快照（不含链路日志），
 * 与 CelestialGenerationHistory 相互独立——历史删除不影响收藏，收藏取消不影响历史。
 */
@Service
public class FavoriteGenerationService {

	private final FavoriteGenerationRepository favoriteGenerationRepository;
	private final CelestialGenerationHistoryRepository historyRepository;
	private final ObjectMapper objectMapper;

	public FavoriteGenerationService(FavoriteGenerationRepository favoriteGenerationRepository,
			CelestialGenerationHistoryRepository historyRepository, ObjectMapper objectMapper) {
		this.favoriteGenerationRepository = favoriteGenerationRepository;
		this.historyRepository = historyRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public List<FavoriteGenerationDTO> list(Long userId) {
		return favoriteGenerationRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
				.map(this::toDTO)
				.toList();
	}

	@Transactional
	public List<FavoriteGenerationDTO> add(Long userId, Long generationId) {
		if (favoriteGenerationRepository.findByUserIdAndGenerationId(userId, generationId).isEmpty()) {
			CelestialGenerationHistory history = historyRepository.findByIdAndUserId(generationId, userId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该生成记录"));
			favoriteGenerationRepository.save(new FavoriteGeneration(userId, generationId,
					history.getName(), history.getType(), history.getParametersJson(),
					history.getIntroduction(), history.getRenderJson(), history.getImageUrl()));
		}
		return list(userId);
	}

	@Transactional
	public List<FavoriteGenerationDTO> remove(Long userId, Long generationId) {
		favoriteGenerationRepository.deleteByUserIdAndGenerationId(userId, generationId);
		return list(userId);
	}

	private FavoriteGenerationDTO toDTO(FavoriteGeneration favorite) {
		return new FavoriteGenerationDTO(
				favorite.getGenerationId(), favorite.getName(), favorite.getType(),
				fromJson(favorite.getParametersJson(), new TypeReference<Map<String, String>>() { }),
				favorite.getIntroduction(), fromJson(favorite.getRenderJson(), RenderSpec.class),
				favorite.getImageUrl(), favorite.getCreatedAt());
	}

	private <T> T fromJson(String json, Class<T> type) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(json, type);
		} catch (Exception e) {
			return null;
		}
	}

	private <T> T fromJson(String json, TypeReference<T> type) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(json, type);
		} catch (Exception e) {
			return null;
		}
	}
}
