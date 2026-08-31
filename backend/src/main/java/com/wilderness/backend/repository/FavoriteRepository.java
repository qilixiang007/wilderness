package com.wilderness.backend.repository;

import com.wilderness.backend.domain.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

	List<Favorite> findByUserIdOrderByCreatedAtAsc(Long userId);

	Optional<Favorite> findByUserIdAndSlug(Long userId, String slug);

	void deleteByUserIdAndSlug(Long userId, String slug);
}
