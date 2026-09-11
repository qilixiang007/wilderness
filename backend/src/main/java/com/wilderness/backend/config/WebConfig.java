package com.wilderness.backend.config;

import com.wilderness.backend.auth.AuthInterceptor;
import com.wilderness.backend.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：注册鉴权拦截器（作用于所有 /api/** 路径）+ 限流拦截器
 * （作用于 /api/ai/** 和 /api/knowledge/upload，且必须排在鉴权拦截器之后——
 * 限流要按 userId/IP 分维度，userId 是鉴权拦截器在 preHandle 里写入 AuthContext 的）。
 * 静态资源 /images/** 不走拦截器，天然公开。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final AuthInterceptor authInterceptor;
	private final RateLimitInterceptor rateLimitInterceptor;

	public WebConfig(AuthInterceptor authInterceptor, RateLimitInterceptor rateLimitInterceptor) {
		this.authInterceptor = authInterceptor;
		this.rateLimitInterceptor = rateLimitInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(authInterceptor).addPathPatterns("/api/**").order(1);
		registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/ai/**", "/api/knowledge/upload").order(2);
	}
}
