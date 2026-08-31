package com.wilderness.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 发送验证码请求。purpose: register | login。
 */
public record VerifyCodeRequest(
		@NotBlank @Email(message = "邮箱格式不正确") String email,
		String purpose) {
}
