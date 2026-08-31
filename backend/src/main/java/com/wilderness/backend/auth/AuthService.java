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
	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

	public AuthService(UserRepository userRepository,
			VerifyCodeService verifyCodeService,
			SessionService sessionService,
			AuthProperties props) {
		this.userRepository = userRepository;
		this.verifyCodeService = verifyCodeService;
		this.sessionService = sessionService;
		this.props = props;
	}

	public LoginResult register(String email, String password, String code) {
		email = User.normalizeEmail(email);
		requireEmail(email);
		if (password == null || password.length() < 6) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码长度至少 6 位");
		}
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

	public LoginResult loginPassword(String email, String password) {
		email = User.normalizeEmail(email);
		requireEmail(email);
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误"));
		if (!encoder.matches(password == null ? "" : password, user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误");
		}
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
