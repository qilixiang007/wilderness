package com.wilderness.backend.service;

import com.wilderness.backend.domain.Category;
import com.wilderness.backend.domain.CelestialObject;
import com.wilderness.backend.dto.CategoryDetailDTO;
import com.wilderness.backend.dto.CategorySummaryDTO;
import com.wilderness.backend.dto.ObjectSummaryDTO;
import com.wilderness.backend.repository.CategoryRepository;
import com.wilderness.backend.repository.CelestialObjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CategoryService {

	private final CategoryRepository categoryRepository;
	private final CelestialObjectRepository celestialObjectRepository;

	public CategoryService(CategoryRepository categoryRepository, CelestialObjectRepository celestialObjectRepository) {
		this.categoryRepository = categoryRepository;
		this.celestialObjectRepository = celestialObjectRepository;
	}

	@Transactional(readOnly = true)
	public List<CategorySummaryDTO> findAll() {
		Map<String, Long> counts = new HashMap<>();
		for (Object[] row : categoryRepository.countObjectsPerCategory()) {
			counts.put((String) row[0], (Long) row[1]);
		}
		return categoryRepository.findAllByOrderBySortOrderAsc().stream()
				.map(category -> new CategorySummaryDTO(
						category.getSlug(),
						category.getZhName(),
						category.getEnName(),
						category.getZhDescription(),
						category.getEnDescription(),
						category.getImage(),
						category.getImageAltZh(),
						category.getImageAltEn(),
						counts.getOrDefault(category.getSlug(), 0L)))
				.toList();
	}

	@Transactional(readOnly = true)
	public CategoryDetailDTO findBySlug(String slug) {
		Category category = categoryRepository.findBySlug(slug)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + slug));
		List<ObjectSummaryDTO> objects = celestialObjectRepository.findByCategorySlugOrderBySortOrderAsc(slug).stream()
				.map(this::toSummary)
				.toList();
		return new CategoryDetailDTO(
				category.getSlug(),
				category.getZhName(),
				category.getEnName(),
				category.getZhDescription(),
				category.getEnDescription(),
				category.getImage(),
				category.getImageAltZh(),
				category.getImageAltEn(),
				objects);
	}

	private ObjectSummaryDTO toSummary(CelestialObject object) {
		return new ObjectSummaryDTO(
				object.getSlug(),
				object.getZhName(),
				object.getEnName(),
				object.getZhDescription(),
				object.getEnDescription(),
				object.getImage(),
				object.getSortOrder());
	}
}
