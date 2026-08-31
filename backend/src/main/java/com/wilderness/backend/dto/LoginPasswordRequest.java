package com.wilderness.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 邮箱 + 密码登录请求。
 */
public record LoginPasswordRequest(
		@NotBlank @Email(message = "邮箱格式不正确") String email,
		@NotBlank String password) {
}
