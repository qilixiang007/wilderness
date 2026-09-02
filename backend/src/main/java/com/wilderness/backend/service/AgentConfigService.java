package com.wilderness.backend.service;

import com.wilderness.backend.domain.AgentConfig;
import com.wilderness.backend.dto.AgentConfigDTO;
import com.wilderness.backend.dto.AgentConfigRequest;
import com.wilderness.backend.repository.AgentConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 用户自定义智能体的增删改查（按用户隔离）。
 * 越权访问他人智能体一律返回 404（不暴露存在性）。
 */
@Service
public class AgentConfigService {

	private final AgentConfigRepository repository;

	public AgentConfigService(AgentConfigRepository repository) {
		this.repository = repository;
	}

	public List<AgentConfigDTO> list(Long userId) {
		return repository.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::toDto).toList();
	}

	public AgentConfigDTO create(Long userId, AgentConfigRequest req) {
		AgentConfig agent = new AgentConfig(userId, req.name(), req.systemPrompt(),
				req.knowledgeSearchEnabled(), req.imageGenEnabled());
		return toDto(repository.save(agent));
	}

	public AgentConfigDTO update(Long userId, Long id, AgentConfigRequest req) {
		AgentConfig agent = getOwned(userId, id);
		agent.update(req.name(), req.systemPrompt(), req.knowledgeSearchEnabled(), req.imageGenEnabled());
		return toDto(repository.save(agent));
	}

	public void delete(Long userId, Long id) {
		repository.delete(getOwned(userId, id));
	}

	/** 取当前用户拥有的智能体；不存在或非本人 → 404。 */
	public AgentConfig getOwned(Long userId, Long id) {
		return repository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "智能体不存在"));
	}

	private AgentConfigDTO toDto(AgentConfig a) {
		return new AgentConfigDTO(a.getId(), a.getName(), a.getSystemPrompt(),
				a.isKnowledgeSearchEnabled(), a.isImageGenEnabled(), a.getCreatedAt(), a.getUpdatedAt());
	}
}
