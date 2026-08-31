package com.wilderness.backend.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收藏接口：必须登录(拦截器强制) + 按用户隔离。
 * 会话还原走 mock Redis：session:{token} → userId。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FavoriteControllerIntegrationTest {

	private static final Cookie USER_A = new Cookie("wilderness_session", "tok-u1");
	private static final Cookie USER_B = new Cookie("wilderness_session", "tok-u2");

	@MockitoBean
	private StringRedisTemplate redis;

	@MockitoBean
	private ValueOperations<String, String> valueOps;

	@Autowired
	private MockMvc mockMvc;

	@BeforeEach
	void stubRedis() {
		when(redis.opsForValue()).thenReturn(valueOps);
		// 兜底必须在前:Mockito 对同一方法多次 stub 以最后注册者优先,具体值随后注册才不会被 anyString 覆盖
		when(valueOps.get(anyString())).thenReturn(null);
		when(valueOps.get("session:tok-u1")).thenReturn("101");
		when(valueOps.get("session:tok-u2")).thenReturn("202");
	}

	/** 未登录收藏/查看 → 401。 */
	@Test
	void favoritesRequireLogin() throws Exception {
		mockMvc.perform(put("/api/favorites/sun"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/favorites"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/favorites/sun"))
				.andExpect(status().isUnauthorized());
	}

	/** 收藏 → 列表 → 取消收藏 完整流程。 */
	@Test
	void addListRemoveFlow() throws Exception {
		mockMvc.perform(put("/api/favorites/sun").cookie(USER_A))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].slug").value("sun"));

		mockMvc.perform(get("/api/favorites").cookie(USER_A))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].slug").value("sun"));

		mockMvc.perform(delete("/api/favorites/sun").cookie(USER_A))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

	/** 收藏不存在的天体 → 404。 */
	@Test
	void addUnknownObjectReturns404() throws Exception {
		mockMvc.perform(put("/api/favorites/not-real").cookie(USER_A))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false));
	}

	/** 两个用户的收藏互不可见。 */
	@Test
	void favoritesAreIsolatedBetweenUsers() throws Exception {
		mockMvc.perform(put("/api/favorites/sun").cookie(USER_A)).andExpect(status().isOk());
		mockMvc.perform(put("/api/favorites/mars").cookie(USER_B)).andExpect(status().isOk());

		mockMvc.perform(get("/api/favorites").cookie(USER_A))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].slug").value("sun"));

		mockMvc.perform(get("/api/favorites").cookie(USER_B))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].slug").value("mars"));
	}

	/** 重复收藏幂等：不产生重复行。 */
	@Test
	void addIsIdempotent() throws Exception {
		mockMvc.perform(put("/api/favorites/sun").cookie(USER_A)).andExpect(status().isOk());
		mockMvc.perform(put("/api/favorites/sun").cookie(USER_A)).andExpect(status().isOk());

		mockMvc.perform(get("/api/favorites").cookie(USER_A))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1));
	}
}
