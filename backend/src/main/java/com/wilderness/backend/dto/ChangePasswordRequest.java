package com.wilderness.backend.dto;

import com.wilderness.backend.auth.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 登录状态下修改密码：原密码 + 新密码。
 */
public record ChangePasswordRequest(
		@NotBlank String oldPassword,
		@NotBlank @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE) String newPassword) {
}
