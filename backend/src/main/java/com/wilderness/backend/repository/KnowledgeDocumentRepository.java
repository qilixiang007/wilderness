package com.wilderness.backend.repository;

import com.wilderness.backend.domain.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

	List<KnowledgeDocument> findByUserIdOrderByUploadedAtDesc(Long userId);

	Optional<KnowledgeDocument> findByUserIdAndFileName(Long userId, String fileName);

	Optional<KnowledgeDocument> findByIdAndUserId(Long id, Long userId);
}
