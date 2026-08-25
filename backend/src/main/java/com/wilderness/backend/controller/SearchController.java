package com.wilderness.backend.controller;

import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.ObjectSummaryDTO;
import com.wilderness.backend.service.CelestialObjectService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchController {

	private final CelestialObjectService celestialObjectService;

	public SearchController(CelestialObjectService celestialObjectService) {
		this.celestialObjectService = celestialObjectService;
	}

	@GetMapping
	public ApiResponse<List<ObjectSummaryDTO>> search(@RequestParam("q") String q) {
		if (q == null || q.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query 'q' must not be blank");
		}
		return ApiResponse.ok(celestialObjectService.search(q));
	}
}
