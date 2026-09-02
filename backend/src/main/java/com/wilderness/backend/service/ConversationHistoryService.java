package com.wilderness.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wilderness.backend.ai.history.ConversationEsWriter;
import com.wilderness.backend.domain.ConversationMessage;
import com.wilderness.backend.dto.AiSource;
import com.wilderness.backend.dto.ConversationMessageDTO;
import com.wilderness.backend.dto.ConversationPageDTO;
import com.wilderness.backend.repository.ConversationMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 「对话历史」模块：当前用户自己的问答记录（按用户维度隔离）。
 * 分页倒序返回；删除时同步清理 MySQL 权威数据与 ES 分析索引。
 */
@Service
public class ConversationHistoryService {

    private final ConversationMessageRepository repository;
    private final ObjectMapper objectMapper;
    private final ConversationEsWriter esWriter;

    public ConversationHistoryService(ConversationMessageRepository repository,
                                      ObjectMapper objectMapper,
                                      ConversationEsWriter esWriter) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.esWriter = esWriter;
    }

    public ConversationPageDTO list(Long userId, String q, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ConversationMessage> result;
        if (q != null && !q.isBlank()) {
            result = repository.findByUserIdAndQuestionContainingOrderByCreatedAtDesc(userId, q.trim(), pageable);
        } else {
            result = repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        List<ConversationMessageDTO> items = result.getContent().stream().map(this::toDto).toList();
        return new ConversationPageDTO(items, result.getTotalElements(), result.getTotalPages(),
                result.getNumber(), result.getSize());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        ConversationMessage message = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
        repository.delete(message);
        esWriter.delete(id);
    }

    private ConversationMessageDTO toDto(ConversationMessage message) {
        List<AiSource> sources;
        try {
            sources = objectMapper.readValue(message.getSourcesJson(), new TypeReference<List<AiSource>>() { });
        } catch (Exception e) {
            sources = List.of();
        }
        return new ConversationMessageDTO(message.getId(), message.getQuestion(), message.getAnswer(),
                sources, message.isWebEnabled(), message.getCreatedAt());
    }
}
