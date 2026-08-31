package com.wilderness.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 邮箱注册请求。code 可选：默认注册即设置密码；传入验证码可校验邮箱真实性。
 */
public record RegisterRequest(
		@NotBlank @Email(message = "邮箱格式不正确") String email,
		@NotBlank @Size(min = 6, max = 64, message = "密码长度至少 6 位") String password,
		String code) {
}
