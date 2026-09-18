package com.wilderness.backend.ai;

import com.wilderness.backend.domain.CelestialObject;
import com.wilderness.backend.repository.CelestialObjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 天体目录(DataSeeder 播种进 MySQL)和知识库语料(classpath:knowledge/*.md 入 ES)是两份独立维护的数据。
 * 目录里有、语料里没有的天体,详情页正常,但讲解/对比会因 ES 零命中而失败——
 * 这里在构建阶段拦住这种漂移,新增天体时必须同时补一篇语料。
 */
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeCorpusCoverageTest {

	@Autowired
	private CelestialObjectRepository celestialObjectRepository;

	// test profile 排除了 Redis 自动配置,但 auth 服务需要 StringRedisTemplate,补一个 mock 让 context 可加载
	@MockitoBean
	private StringRedisTemplate redis;

	@Test
	void 目录中每个天体都有对应的知识库语料() throws Exception {
		Set<String> corpusSlugs = new HashSet<>();
		MarkdownFrontmatterParser parser = new MarkdownFrontmatterParser();
		for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath:knowledge/*.md")) {
			String text = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			corpusSlugs.add(parser.parse(text).slug());
		}

		List<String> missing = celestialObjectRepository.findAll().stream()
				.map(CelestialObject::getSlug)
				.filter(slug -> !corpusSlugs.contains(slug))
				.sorted()
				.toList();

		assertTrue(missing.isEmpty(), "以下天体缺少知识库语料 knowledge/<slug>.md,讲解与对比会失败: " + missing);
	}
}
