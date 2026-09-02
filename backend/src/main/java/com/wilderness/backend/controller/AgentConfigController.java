package com.wilderness.backend.controller;

import com.wilderness.backend.ai.agent.CelestialAgentService;
import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.AgentConfigDTO;
import com.wilderness.backend.dto.AgentConfigRequest;
import com.wilderness.backend.dto.AgentGenerateRequest;
import com.wilderness.backend.dto.GenerationResult;
import com.wilderness.backend.service.AgentConfigService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户自定义智能体接口（按用户隔离）。
 * generate 依赖天体生成 Agent（DashScope），与 AiController 相同门控：未配置 key 时整体 404。
 */
@RestController
@RequestMapping("/api/agents")
@ConditionalOnExpression("!('${wilderness.ai.dashscope.api-key:}'.trim().isEmpty())")
public class AgentConfigController {

	private final AgentConfigService agentConfigService;
	private final CelestialAgentService celestialAgentService;

	public AgentConfigController(AgentConfigService agentConfigService, CelestialAgentService celestialAgentService) {
		this.agentConfigService = agentConfigService;
		this.celestialAgentService = celestialAgentService;
	}

	@GetMapping
	public ApiResponse<List<AgentConfigDTO>> list() {
		return ApiResponse.ok(agentConfigService.list(AuthContext.currentUserId()));
	}

	@PostMapping
	public ApiResponse<AgentConfigDTO> create(@Valid @RequestBody AgentConfigRequest request) {
		return ApiResponse.ok(agentConfigService.create(AuthContext.currentUserId(), request));
	}

	@PutMapping("/{id}")
	public ApiResponse<AgentConfigDTO> update(@PathVariable Long id, @Valid @RequestBody AgentConfigRequest request) {
		return ApiResponse.ok(agentConfigService.update(AuthContext.currentUserId(), id, request));
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@PathVariable Long id) {
		agentConfigService.delete(AuthContext.currentUserId(), id);
		return ApiResponse.ok("已删除", null);
	}

	/** 使用某个自定义智能体生成天体（人设提示词 + 工具开关生效）。 */
	@PostMapping("/{id}/generate")
	public ApiResponse<GenerationResult> generate(@PathVariable Long id, @Valid @RequestBody AgentGenerateRequest request) {
		return ApiResponse.ok(celestialAgentService.generateWithAgent(AuthContext.currentUserId(), id, request.description()));
	}
}
