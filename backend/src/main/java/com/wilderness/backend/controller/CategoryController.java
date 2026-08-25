package com.wilderness.backend.controller;

import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.dto.CategoryDetailDTO;
import com.wilderness.backend.dto.CategorySummaryDTO;
import com.wilderness.backend.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@GetMapping
	public ApiResponse<List<CategorySummaryDTO>> list() {
		return ApiResponse.ok(categoryService.findAll());
	}

	@GetMapping("/{slug}")
	public ApiResponse<CategoryDetailDTO> detail(@PathVariable String slug) {
		return ApiResponse.ok(categoryService.findBySlug(slug));
	}
}
