package com.wilderness.backend.auth;

import com.wilderness.backend.config.AuthProperties;
import com.wilderness.backend.domain.User;
import com.wilderness.backend.dto.UserDTO;
import com.wilderness.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 注册 / 登录业务：密码 BCrypt 哈希、验证码校验、会话创建。
 * 注册与登录成功后直接建立会话（注册即登录）。
 */
@Service
public class AuthService {

	private final UserRepository userRepository;
	private final VerifyCodeService verifyCodeService;
	private final SessionService sessionService;
	private final AuthProperties props;
	private final LoginAttemptService loginAttemptService;
	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

	public AuthService(UserRepository userRepository,
			VerifyCodeService verifyCodeService,
			SessionService sessionService,
			AuthProperties props,
			LoginAttemptService loginAttemptService) {
		this.userRepository = userRepository;
		this.verifyCodeService = verifyCodeService;
		this.sessionService = sessionService;
		this.props = props;
		this.loginAttemptService = loginAttemptService;
	}

	public LoginResult register(String email, String password, String code) {
		email = User.normalizeEmail(email);
		requireEmail(email);
		PasswordPolicy.validate(password);
		if (userRepository.existsByEmail(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "该邮箱已注册，请直接登录");
		}
		if (code != null && !code.isBlank()) {
			verifyCodeService.verify(email, "register", code);
		} else if (props.requireEmailVerify()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "注册需要邮箱验证码");
		}
		User user = userRepository.save(new User(email, encoder.encode(password)));
		return loginResult(user);
	}

	/** 密码登录；同一邮箱连续失败达到阈值会被暂时锁定，防止暴力破解密码。 */
	public LoginResult loginPassword(String email, String password) {
		email = User.normalizeEmail(email);
		requireEmail(email);
		loginAttemptService.assertNotLocked(email);
		User user = userRepository.findByEmail(email).orElse(null);
		boolean matched = user != null
				&& encoder.matches(password == null ? "" : password, user.getPasswordHash());
		if (!matched) {
			loginAttemptService.onFailure(email);
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误");
		}
		loginAttemptService.onSuccess(email);
		return loginResult(user);
	}

	public LoginResult loginCode(String email, String code) {
		email = User.normalizeEmail(email);
		requireEmail(email);
		verifyCodeService.verify(email, "login", code);
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在，请先注册"));
		return loginResult(user);
	}

	/** 找回密码：校验邮箱验证码（purpose=reset）后，用新密码覆盖旧密码哈希。 */
	@Transactional
	public void resetPassword(String email, String code, String newPassword) {
		email = User.normalizeEmail(email);
		requireEmail(email);
		PasswordPolicy.validate(newPassword);
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "该邮箱尚未注册"));
		verifyCodeService.verify(email, "reset", code);
		user.updatePasswordHash(encoder.encode(newPassword));
		userRepository.save(user);
	}

	/** 登录状态下修改密码：需校验原密码，且原密码连续猜错达到阈值会被暂时锁定。 */
	@Transactional
	public void changePassword(Long userId, String oldPassword, String newPassword) {
		if (userId == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
		}
		PasswordPolicy.validate(newPassword);
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "会话已失效，请重新登录"));
		String lockKey = "chpwd:" + userId;
		loginAttemptService.assertNotLocked(lockKey);
		if (!encoder.matches(oldPassword == null ? "" : oldPassword, user.getPasswordHash())) {
			loginAttemptService.onFailure(lockKey);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原密码错误");
		}
		loginAttemptService.onSuccess(lockKey);
		user.updatePasswordHash(encoder.encode(newPassword));
		userRepository.save(user);
	}

	/** 登出：删除 Redis 中的会话。token 为空时静默成功（幂等）。 */
	public void logout(String token) {
		if (token != null && !token.isBlank()) {
			sessionService.delete(token);
		}
	}

	@Transactional(readOnly = true)
	public UserDTO me(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "会话已失效，请重新登录"));
		return new UserDTO(user.getId(), user.getEmail());
	}

	private LoginResult loginResult(User user) {
		String token = sessionService.createSession(user.getId());
		return new LoginResult(token, new UserDTO(user.getId(), user.getEmail()));
	}

	private void requireEmail(String email) {
		if (email == null || email.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
		}
	}
}
