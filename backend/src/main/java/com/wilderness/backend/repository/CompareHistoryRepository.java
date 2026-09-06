package com.wilderness.backend.repository;

import com.wilderness.backend.domain.CompareHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompareHistoryRepository extends JpaRepository<CompareHistory, Long> {

	Page<CompareHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	Optional<CompareHistory> findByIdAndUserId(Long id, Long userId);
}
