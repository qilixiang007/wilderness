package com.wilderness.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.domain.CelestialGenerationHistory;
import com.wilderness.backend.domain.FavoriteGeneration;
import com.wilderness.backend.dto.FavoriteGenerationDTO;
import com.wilderness.backend.dto.RenderSpec;
import com.wilderness.backend.repository.CelestialGenerationHistoryRepository;
import com.wilderness.backend.repository.FavoriteGenerationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 「自建天体」收藏业务：按用户隔离。收藏存生成结果快照（不含链路日志），
 * 与 CelestialGenerationHistory 相互独立——历史删除不影响收藏，收藏取消不影响历史。
 * 图片同理各存一份独立文件：收藏时若源图已本地持久化则复制一份；若源图还是外部临时链接
 * 则借收藏这个动作尝试重新下载转存一次（等于"收藏"顺带抢救了一张即将过期的图）。
 */
@Service
public class FavoriteGenerationService {

	private static final Logger log = LoggerFactory.getLogger(FavoriteGenerationService.class);

	private final FavoriteGenerationRepository favoriteGenerationRepository;
	private final CelestialGenerationHistoryRepository historyRepository;
	private final GeneratedImageStorageService imageStorageService;
	private final ObjectMapper objectMapper;

	public FavoriteGenerationService(FavoriteGenerationRepository favoriteGenerationRepository,
			CelestialGenerationHistoryRepository historyRepository, GeneratedImageStorageService imageStorageService,
			ObjectMapper objectMapper) {
		this.favoriteGenerationRepository = favoriteGenerationRepository;
		this.historyRepository = historyRepository;
		this.imageStorageService = imageStorageService;
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
			String imageUrl = history.getImageUrl();
			boolean imageTemporary = history.isImageTemporary();
			Long imageId = null;
			if (imageUrl != null && !imageTemporary && history.getImageId() != null) {
				// 源图已本地持久化：复制一份收藏自己独立的文件
				try {
					GeneratedImageStorageService.StoredImage copied = imageStorageService.copyLocal(history.getImageId(), userId);
					imageId = copied.id();
					imageUrl = copied.relativeUrl();
				} catch (Exception e) {
					log.warn("favorite generation copy local image failed, generationId={}", generationId, e);
					imageUrl = null; // 复制失败极少发生；降级为不带图，比中断收藏更合理
				}
			} else if (imageUrl != null && imageTemporary) {
				// 源图还是外部临时链接：借收藏这次尝试重新下载转存，等于顺带抢救一次
				try {
					GeneratedImageStorageService.StoredImage downloaded = imageStorageService.download(imageUrl, userId);
					imageId = downloaded.id();
					imageUrl = downloaded.relativeUrl();
					imageTemporary = false;
				} catch (Exception e) {
					log.warn("favorite generation download image failed, generationId={}", generationId, e);
					// 抢救失败：原样保留这个临时链接（可能已经过期），imageTemporary 保持 true
				}
			}
			favoriteGenerationRepository.save(new FavoriteGeneration(userId, generationId,
					history.getName(), history.getType(), history.getParametersJson(),
					history.getIntroduction(), history.getRenderJson(), imageUrl, imageTemporary, imageId));
		}
		return list(userId);
	}

	@Transactional
	public List<FavoriteGenerationDTO> remove(Long userId, Long generationId) {
		favoriteGenerationRepository.findByUserIdAndGenerationId(userId, generationId).ifPresent(favorite -> {
			favoriteGenerationRepository.delete(favorite);
			if (favorite.getImageId() != null) {
				imageStorageService.delete(favorite.getImageId());
			}
		});
		return list(userId);
	}

	private FavoriteGenerationDTO toDTO(FavoriteGeneration favorite) {
		return new FavoriteGenerationDTO(
				favorite.getGenerationId(), favorite.getName(), favorite.getType(),
				fromJson(favorite.getParametersJson(), new TypeReference<Map<String, String>>() { }),
				favorite.getIntroduction(), fromJson(favorite.getRenderJson(), RenderSpec.class),
				favorite.getImageUrl(), favorite.isImageTemporary(), favorite.getCreatedAt());
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
