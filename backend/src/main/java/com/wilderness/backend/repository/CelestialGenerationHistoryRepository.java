package com.wilderness.backend.repository;

import com.wilderness.backend.domain.CelestialGenerationHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CelestialGenerationHistoryRepository extends JpaRepository<CelestialGenerationHistory, Long> {

	Page<CelestialGenerationHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

	Optional<CelestialGenerationHistory> findByIdAndUserId(Long id, Long userId);
}
