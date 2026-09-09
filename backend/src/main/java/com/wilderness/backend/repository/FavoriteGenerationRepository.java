package com.wilderness.backend.repository;

import com.wilderness.backend.domain.FavoriteGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteGenerationRepository extends JpaRepository<FavoriteGeneration, Long> {

	List<FavoriteGeneration> findByUserIdOrderByCreatedAtAsc(Long userId);

	Optional<FavoriteGeneration> findByUserIdAndGenerationId(Long userId, Long generationId);

	void deleteByUserIdAndGenerationId(Long userId, Long generationId);
}
