package com.wilderness.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.auth.AuthService;
import com.wilderness.backend.domain.CelestialGenerationHistory;
import com.wilderness.backend.domain.User;
import com.wilderness.backend.dto.AgentStep;
import com.wilderness.backend.dto.AiSource;
import com.wilderness.backend.dto.CelestialGenerationDetailDTO;
import com.wilderness.backend.dto.CelestialGenerationPageDTO;
import com.wilderness.backend.dto.CelestialGenerationSummaryDTO;
import com.wilderness.backend.dto.GenerationResult;
import com.wilderness.backend.dto.RenderSpec;
import com.wilderness.backend.repository.CelestialGenerationHistoryRepository;
import com.wilderness.backend.repository.UserRepository;
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
import java.util.Map;

/**
 * 「天体生成历史」模块：当前用户自己的生成记录 + 完整链路日志（按用户维度隔离）。
 * 纯 MySQL，不走 MQ/ES；save 尽力而为、绝不影响生成主流程（照抄 CompareHistoryService）。
 * 失败/降级的生成同样落库——完整日志正是为了后续可追溯类似问题。
 */
@Service
public class CelestialGenerationHistoryService {

	private static final Logger log = LoggerFactory.getLogger(CelestialGenerationHistoryService.class);

	private final CelestialGenerationHistoryRepository repository;
	private final ObjectMapper objectMapper;
	private final GeneratedImageStorageService imageStorageService;
	private final UserRepository userRepository;
	private final AuthService authService;

	public CelestialGenerationHistoryService(CelestialGenerationHistoryRepository repository, ObjectMapper objectMapper,
			GeneratedImageStorageService imageStorageService, UserRepository userRepository, AuthService authService) {
		this.repository = repository;
		this.objectMapper = objectMapper;
		this.imageStorageService = imageStorageService;
		this.userRepository = userRepository;
		this.authService = authService;
	}

	/**
	 * 未登录（userId 为 null）不落库；序列化/落库失败只记日志，不向上抛。
	 * 返回落库后的行 id（供收藏功能引用）；未落库/失败返回 null。
	 * imageId：本地持久化图对应的 GeneratedImage 行 id，外部临时链接/无图传 null。
	 */
	public Long save(Long userId, Long agentId, String agentName, String description, GenerationResult result,
			boolean imageTemporary, Long imageId, String systemPrompt, String userPrompt, String referenceText,
			String rawModelResponse, String langsmithRunId, boolean success, String errorMessage) {
		if (userId == null) {
			return null;
		}
		try {
			String parametersJson = toJson(result.parameters());
			String renderJson = toJson(result.render());
			String sourcesJson = toJson(result.sources());
			String stepsJson = toJson(result.steps());
			CelestialGenerationHistory saved = repository.save(new CelestialGenerationHistory(userId, agentId, agentName, description,
					result.name(), result.type(), parametersJson, result.introduction(), renderJson,
					result.imageUrl(), imageTemporary, imageId, sourcesJson, stepsJson, referenceText, systemPrompt,
					userPrompt, rawModelResponse, langsmithRunId, success, errorMessage));
			return saved.getId();
		} catch (Exception e) {
			log.warn("celestial generation history save failed, userId={}", userId, e);
			return null;
		}
	}

	public CelestialGenerationPageDTO list(Long userId, int page, int size) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 50);
		PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

		Page<CelestialGenerationHistory> result = repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
		List<CelestialGenerationSummaryDTO> items = result.getContent().stream().map(this::toSummaryDto).toList();
		return new CelestialGenerationPageDTO(items, result.getTotalElements(), result.getTotalPages(),
				result.getNumber(), result.getSize());
	}

	public CelestialGenerationDetailDTO detail(Long userId, Long id) {
		CelestialGenerationHistory history = repository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
		return toDetailDto(history);
	}

	@Transactional
	public void delete(Long userId, Long id) {
		CelestialGenerationHistory history = repository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
		repository.delete(history);
		if (history.getImageId() != null) {
			imageStorageService.delete(history.getImageId());
		}
	}

	/**
	 * 切换可见性：本人可改自己的记录；非本人时若当前用户是管理员，可改任意记录（用于广场下架）。
	 * 都不满足则 404，不暴露记录是否存在。
	 */
	@Transactional
	public boolean setVisibility(Long userId, Long id, boolean isPublic) {
		CelestialGenerationHistory history = repository.findByIdAndUserId(id, userId).orElse(null);
		if (history == null) {
			User currentUser = userRepository.findById(userId).orElse(null);
			boolean admin = currentUser != null && authService.isAdmin(currentUser.getEmail());
			if (!admin) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
			}
			history = repository.findById(id)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
		}
		history.setPublic(isPublic);
		repository.save(history);
		return history.isPublic();
	}

	private CelestialGenerationSummaryDTO toSummaryDto(CelestialGenerationHistory history) {
		return new CelestialGenerationSummaryDTO(
				history.getId(), history.getName(), history.getType(), history.getImageUrl(),
				history.isImageTemporary(), fromJson(history.getRenderJson(), RenderSpec.class), history.getAgentName(),
				history.isSuccess(), history.getCreatedAt(), history.isPublic());
	}

	private CelestialGenerationDetailDTO toDetailDto(CelestialGenerationHistory history) {
		return new CelestialGenerationDetailDTO(
				history.getId(), history.getDescription(), history.getName(), history.getType(),
				fromJson(history.getParametersJson(), new TypeReference<Map<String, String>>() { }),
				history.getIntroduction(), fromJson(history.getRenderJson(), RenderSpec.class), history.getImageUrl(),
				history.isImageTemporary(),
				fromJson(history.getSourcesJson(), new TypeReference<List<AiSource>>() { }),
				fromJson(history.getStepsJson(), new TypeReference<List<AgentStep>>() { }),
				history.getAgentName(), history.isSuccess(), history.getErrorMessage(),
				history.getReferenceText(), history.getSystemPrompt(), history.getUserPrompt(),
				history.getRawModelResponse(), history.getLangsmithRunId(), history.getCreatedAt());
	}

	private String toJson(Object o) {
		try {
			return o == null ? null : objectMapper.writeValueAsString(o);
		} catch (Exception e) {
			return null;
		}
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
