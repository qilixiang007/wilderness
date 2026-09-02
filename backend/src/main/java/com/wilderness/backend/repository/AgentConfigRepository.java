package com.wilderness.backend.repository;

import com.wilderness.backend.domain.AgentConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentConfigRepository extends JpaRepository<AgentConfig, Long> {

	List<AgentConfig> findByUserIdOrderByUpdatedAtDesc(Long userId);

	Optional<AgentConfig> findByIdAndUserId(Long id, Long userId);
}
