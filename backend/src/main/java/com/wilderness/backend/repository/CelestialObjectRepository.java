package com.wilderness.backend.repository;

import com.wilderness.backend.domain.CelestialObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CelestialObjectRepository extends JpaRepository<CelestialObject, Long> {

	Optional<CelestialObject> findBySlug(String slug);

	List<CelestialObject> findByCategorySlugOrderBySortOrderAsc(String categorySlug);

	List<CelestialObject> findAllByOrderBySortOrderAsc();

	/**
	 * 在 zh/en 名称与描述上做不区分大小写的模糊搜索。
	 * 显式 LOWER() 保证 MySQL 与 H2 的排序规则行为一致。
	 */
	@Query("""
			select o from CelestialObject o
			where lower(o.zhName) like lower(concat('%', :q, '%'))
			   or lower(o.enName) like lower(concat('%', :q, '%'))
			   or lower(o.zhDescription) like lower(concat('%', :q, '%'))
			   or lower(o.enDescription) like lower(concat('%', :q, '%'))
			order by o.sortOrder asc, o.id asc
			""")
	List<CelestialObject> search(@Param("q") String q);
}
