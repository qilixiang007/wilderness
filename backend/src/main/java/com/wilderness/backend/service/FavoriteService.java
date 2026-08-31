package com.wilderness.backend.service;

import com.wilderness.backend.domain.CelestialObject;
import com.wilderness.backend.domain.Favorite;
import com.wilderness.backend.dto.FavoriteDTO;
import com.wilderness.backend.repository.CelestialObjectRepository;
import com.wilderness.backend.repository.FavoriteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 收藏业务：按用户隔离。收藏存天体快照，前端列表零请求即可渲染。
 */
@Service
public class FavoriteService {

	private final FavoriteRepository favoriteRepository;
	private final CelestialObjectRepository celestialObjectRepository;

	public FavoriteService(FavoriteRepository favoriteRepository, CelestialObjectRepository celestialObjectRepository) {
		this.favoriteRepository = favoriteRepository;
		this.celestialObjectRepository = celestialObjectRepository;
	}

	@Transactional(readOnly = true)
	public List<FavoriteDTO> list(Long userId) {
		return favoriteRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
				.map(this::toDTO)
				.toList();
	}

	@Transactional
	public List<FavoriteDTO> add(Long userId, String slug) {
		if (favoriteRepository.findByUserIdAndSlug(userId, slug).isEmpty()) {
			CelestialObject object = celestialObjectRepository.findBySlug(slug)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该天体"));
			favoriteRepository.save(new Favorite(userId, slug,
					object.getZhName(), object.getEnName(), object.getImage()));
		}
		return list(userId);
	}

	@Transactional
	public List<FavoriteDTO> remove(Long userId, String slug) {
		favoriteRepository.deleteByUserIdAndSlug(userId, slug);
		return list(userId);
	}

	private FavoriteDTO toDTO(Favorite favorite) {
		return new FavoriteDTO(favorite.getSlug(), favorite.getZhName(), favorite.getEnName(), favorite.getImage());
	}
}
