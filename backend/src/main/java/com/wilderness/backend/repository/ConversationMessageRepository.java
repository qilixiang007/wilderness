package com.wilderness.backend.repository;

import com.wilderness.backend.domain.ConversationMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

	Page<ConversationMessage> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	Page<ConversationMessage> findByUserIdAndQuestionContainingOrderByCreatedAtDesc(Long userId, String question, Pageable pageable);

	Optional<ConversationMessage> findByIdAndUserId(Long id, Long userId);
}
