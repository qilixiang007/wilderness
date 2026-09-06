package com.wilderness.backend.dto;

import com.wilderness.backend.auth.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 找回密码请求：邮箱 + 验证码（purpose=reset）+ 新密码。
 */
public record ResetPasswordRequest(
		@NotBlank @Email(message = "邮箱格式不正确") String email,
		@NotBlank String code,
		@NotBlank @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String newPassword) {
}
