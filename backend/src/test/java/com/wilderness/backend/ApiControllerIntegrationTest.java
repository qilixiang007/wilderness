package com.wilderness.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	// test profile 排除了 Redis 自动配置,但 auth 服务需要 StringRedisTemplate,补一个 mock 让 context 可加载
	@MockitoBean
	private StringRedisTemplate redis;

	@Test
	void categoriesListsAllWithObjectCounts() throws Exception {
		mockMvc.perform(get("/api/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.length()").value(6))
				.andExpect(jsonPath("$.data[0].slug").value("star"))
				.andExpect(jsonPath("$.data[0].objectCount").value(3));
	}

	@Test
	void categoryDetailIncludesItsObjects() throws Exception {
		mockMvc.perform(get("/api/categories/planet"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.slug").value("planet"))
				.andExpect(jsonPath("$.data.objects.length()").value(8))
				.andExpect(jsonPath("$.data.objects[0].slug").value("mercury"));
	}

	@Test
	void objectDetailIncludesFactsAndCategory() throws Exception {
		mockMvc.perform(get("/api/objects/sun"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.slug").value("sun"))
				.andExpect(jsonPath("$.data.facts.length()").value(5))
				.andExpect(jsonPath("$.data.category.zhName").value("恒星"));
	}

	@Test
	void searchMatchesEnglishTerm() throws Exception {
		mockMvc.perform(get("/api/search").param("q", "mars"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].slug").value("mars"));
	}

	@Test
	void unknownObjectReturns404() throws Exception {
		mockMvc.perform(get("/api/objects/not-real"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false));
	}

	@Test
	void searchWithoutQueryReturns400() throws Exception {
		mockMvc.perform(get("/api/search"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.success").value(false));
	}
}
