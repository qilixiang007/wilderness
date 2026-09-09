package com.wilderness.backend.controller;

import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.CelestialGenerationDetailDTO;
import com.wilderness.backend.dto.CelestialGenerationPageDTO;
import com.wilderness.backend.service.CelestialGenerationHistoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「天体生成历史」接口：当前登录用户自己的生成记录（分页 / 详情含完整链路日志 / 删除）。
 * 落在 /api/history/** 通配符下，AuthInterceptor 已强制要求登录，无需额外鉴权配置。
 */
@RestController
@RequestMapping("/api/history/generation")
public class CelestialGenerationHistoryController {

	private final CelestialGenerationHistoryService celestialGenerationHistoryService;

	public CelestialGenerationHistoryController(CelestialGenerationHistoryService celestialGenerationHistoryService) {
		this.celestialGenerationHistoryService = celestialGenerationHistoryService;
	}

	@GetMapping
	public ApiResponse<CelestialGenerationPageDTO> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ApiResponse.ok(celestialGenerationHistoryService.list(AuthContext.currentUserId(), page, size));
	}

	/** 详情：含完整链路日志（system/user prompt、检索参考资料原文、模型原始返回、LangSmith run id）。 */
	@GetMapping("/{id}")
	public ApiResponse<CelestialGenerationDetailDTO> detail(@PathVariable Long id) {
		return ApiResponse.ok(celestialGenerationHistoryService.detail(AuthContext.currentUserId(), id));
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@PathVariable Long id) {
		celestialGenerationHistoryService.delete(AuthContext.currentUserId(), id);
		return ApiResponse.ok("已删除", null);
	}
}
