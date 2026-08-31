package com.wilderness.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

	// test profile 排除了 Redis 自动配置,但 auth 服务需要 StringRedisTemplate,补一个 mock 让 context 可加载
	@MockitoBean
	private StringRedisTemplate redis;

	@Test
	void contextLoads() {
	}
}
