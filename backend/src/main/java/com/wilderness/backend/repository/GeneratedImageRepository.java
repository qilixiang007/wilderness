package com.wilderness.backend.repository;

import com.wilderness.backend.domain.GeneratedImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GeneratedImageRepository extends JpaRepository<GeneratedImage, Long> {

	Optional<GeneratedImage> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}
