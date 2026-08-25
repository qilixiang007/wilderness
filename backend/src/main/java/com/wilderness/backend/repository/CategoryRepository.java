package com.wilderness.backend.repository;

import com.wilderness.backend.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	Optional<Category> findBySlug(String slug);

	List<Category> findAllByOrderBySortOrderAsc();

	/**
	 * 一次查询拿到每个分类下的天体数，避免对每个分类单独 count（N+1）。
	 *
	 * @return 每行 [分类 slug, 天体数]
	 */
	@Query("select o.category.slug, count(o) from CelestialObject o group by o.category.slug")
	List<Object[]> countObjectsPerCategory();
}
