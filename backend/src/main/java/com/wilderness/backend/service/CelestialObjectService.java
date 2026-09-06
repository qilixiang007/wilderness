package com.wilderness.backend.service;

import com.wilderness.backend.domain.CelestialObject;
import com.wilderness.backend.domain.ObjectFact;
import com.wilderness.backend.dto.CategoryRefDTO;
import com.wilderness.backend.dto.FactDTO;
import com.wilderness.backend.dto.ObjectDetailDTO;
import com.wilderness.backend.dto.ObjectSummaryDTO;
import com.wilderness.backend.repository.CategoryRepository;
import com.wilderness.backend.repository.CelestialObjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CelestialObjectService {

	private final CelestialObjectRepository celestialObjectRepository;
	private final CategoryRepository categoryRepository;

	public CelestialObjectService(CelestialObjectRepository celestialObjectRepository, CategoryRepository categoryRepository) {
		this.celestialObjectRepository = celestialObjectRepository;
		this.categoryRepository = categoryRepository;
	}

	@Transactional(readOnly = true)
	public List<ObjectSummaryDTO> findAll() {
		return celestialObjectRepository.findAllByOrderBySortOrderAsc().stream()
				.map(this::toSummary)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ObjectSummaryDTO> findByCategory(String categorySlug) {
		categoryRepository.findBySlug(categorySlug)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found: " + categorySlug));
		return celestialObjectRepository.findByCategorySlugOrderBySortOrderAsc(categorySlug).stream()
				.map(this::toSummary)
				.toList();
	}

	@Transactional(readOnly = true)
	public ObjectDetailDTO findBySlug(String slug) {
		CelestialObject object = celestialObjectRepository.findBySlug(slug)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Celestial object not found: " + slug));
		List<FactDTO> facts = object.getFacts().stream()
				.sorted(java.util.Comparator.comparingInt(ObjectFact::getSortOrder))
				.map(fact -> new FactDTO(
						fact.getSortOrder(),
						fact.getZhLabel(),
						fact.getEnLabel(),
						fact.getZhValue(),
						fact.getEnValue()))
				.toList();
		return new ObjectDetailDTO(
				object.getSlug(),
				object.getZhName(),
				object.getEnName(),
				object.getZhDescription(),
				object.getEnDescription(),
				object.getImage(),
				object.getSortOrder(),
				new CategoryRefDTO(
						object.getCategory().getSlug(),
						object.getCategory().getZhName(),
						object.getCategory().getEnName()),
				facts,
				object.getDataSource(),
				object.getSourceUrl(),
				object.getSourcedAt() == null ? null : object.getSourcedAt().toString());
	}

	@Transactional(readOnly = true)
	public List<ObjectSummaryDTO> search(String q) {
		return celestialObjectRepository.search(q.trim()).stream()
				.map(this::toSummary)
				.toList();
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
