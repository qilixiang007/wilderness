package com.wilderness.backend.controller;

import com.wilderness.backend.auth.AuthContext;
import com.wilderness.backend.auth.AuthService;
import com.wilderness.backend.auth.LoginResult;
import com.wilderness.backend.auth.SessionService;
import com.wilderness.backend.auth.VerifyCodeService;
import com.wilderness.backend.common.ApiResponse;
import com.wilderness.backend.config.AuthProperties;
import com.wilderness.backend.dto.ChangePasswordRequest;
import com.wilderness.backend.dto.LoginCodeRequest;
import com.wilderness.backend.dto.LoginPasswordRequest;
import com.wilderness.backend.dto.RegisterRequest;
import com.wilderness.backend.dto.ResetPasswordRequest;
import com.wilderness.backend.dto.UserDTO;
import com.wilderness.backend.dto.VerifyCodeRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 认证接口：验证码发送、注册（密码）、密码登录、验证码登录、找回密码、修改密码、登出、当前用户。
 * 注册/登录成功通过 HttpOnly Cookie 写入会话，前端无需存储 token。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final SessionService sessionService;
	private final VerifyCodeService verifyCodeService;
	private final AuthProperties props;

	public AuthController(AuthService authService,
			SessionService sessionService,
			VerifyCodeService verifyCodeService,
			AuthProperties props) {
		this.authService = authService;
		this.sessionService = sessionService;
		this.verifyCodeService = verifyCodeService;
		this.props = props;
	}

	@PostMapping("/verify-code")
	public ApiResponse<String> verifyCode(@Valid @RequestBody VerifyCodeRequest req) {
		String purpose = req.purpose() == null || req.purpose().isBlank() ? "login" : req.purpose();
		String via = verifyCodeService.sendCode(req.email(), purpose);
		// data 返回真实投递方式，前端据此如实展示（log 模式只打在日志，并未发邮件）
		if ("log".equals(via)) {
			return ApiResponse.ok("验证码已记录到服务端日志（开发模式，未发送邮件）", "log");
		}
		return ApiResponse.ok("验证码已发送到你的邮箱", "email");
	}

	@PostMapping("/register")
	public ApiResponse<UserDTO> register(@Valid @RequestBody RegisterRequest req, HttpServletResponse response) {
		LoginResult result = authService.register(req.email(), req.password(), req.code());
		setSessionCookie(response, result.token());
		return ApiResponse.ok("注册成功", result.user());
	}

	@PostMapping("/login-password")
	public ApiResponse<UserDTO> loginPassword(@Valid @RequestBody LoginPasswordRequest req, HttpServletResponse response) {
		LoginResult result = authService.loginPassword(req.email(), req.password());
		setSessionCookie(response, result.token());
		return ApiResponse.ok("登录成功", result.user());
	}

	@PostMapping("/login-code")
	public ApiResponse<UserDTO> loginCode(@Valid @RequestBody LoginCodeRequest req, HttpServletResponse response) {
		LoginResult result = authService.loginCode(req.email(), req.code());
		setSessionCookie(response, result.token());
		return ApiResponse.ok("登录成功", result.user());
	}

	@PostMapping("/reset-password")
	public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
		authService.resetPassword(req.email(), req.code(), req.newPassword());
		return ApiResponse.ok("密码已重置，请使用新密码登录", null);
	}

	@PostMapping("/change-password")
	public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
		authService.changePassword(AuthContext.currentUserId(), req.oldPassword(), req.newPassword());
		return ApiResponse.ok("密码已修改", null);
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		authService.logout(sessionService.tokenFromCookie(request));
		response.addHeader(HttpHeaders.SET_COOKIE,
				ResponseCookie.from(props.cookieName(), "")
						.httpOnly(true)
						.sameSite("Lax")
						.secure(props.cookieSecure())
						.path("/")
						.maxAge(Duration.ZERO)
						.build()
						.toString());
		return ApiResponse.ok("已退出", null);
	}

	@GetMapping("/me")
	public ApiResponse<UserDTO> me() {
		return ApiResponse.ok(authService.me(AuthContext.currentUserId()));
	}

	private void setSessionCookie(HttpServletResponse response, String token) {
		response.addHeader(HttpHeaders.SET_COOKIE,
				ResponseCookie.from(props.cookieName(), token)
						.httpOnly(true)
						.sameSite("Lax")
						.secure(props.cookieSecure())
						.path("/")
						.maxAge(Duration.ofSeconds(props.sessionTtl()))
						.build()
						.toString());
	}
}
