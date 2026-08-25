package com.wilderness.backend.controller;

import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.ObjectDetailDTO;
import com.wilderness.backend.dto.ObjectSummaryDTO;
import com.wilderness.backend.service.CelestialObjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/objects")
public class CelestialObjectController {

	private final CelestialObjectService celestialObjectService;

	public CelestialObjectController(CelestialObjectService celestialObjectService) {
		this.celestialObjectService = celestialObjectService;
	}

	@GetMapping
	public ApiResponse<List<ObjectSummaryDTO>> list(@RequestParam(name = "category", required = false) String categorySlug) {
		List<ObjectSummaryDTO> result = (categorySlug == null || categorySlug.isBlank())
				? celestialObjectService.findAll()
				: celestialObjectService.findByCategory(categorySlug);
		return ApiResponse.ok(result);
	}

	@GetMapping("/{slug}")
	public ApiResponse<ObjectDetailDTO> detail(@PathVariable String slug) {
		return ApiResponse.ok(celestialObjectService.findBySlug(slug));
	}
}
