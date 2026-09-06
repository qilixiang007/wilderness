package com.wilderness.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

/**
 * 密码规则：至少 8 位、至多 64 位，只允许英文字母和数字，且必须同时包含字母与数字。
 * 仅用于新设密码（注册、重置密码），已有账号的旧密码不受影响，仍可正常登录。
 */
public final class PasswordPolicy {

	public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,64}$";
	private static final Pattern PATTERN = Pattern.compile(REGEX);
	public static final String MESSAGE = "密码需为 8-64 位，且必须同时包含英文字母和数字";

	private PasswordPolicy() {
	}

	public static void validate(String password) {
		if (password == null || !PATTERN.matcher(password).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, MESSAGE);
		}
	}
}
