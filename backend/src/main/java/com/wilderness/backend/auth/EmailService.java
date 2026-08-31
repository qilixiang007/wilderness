package com.wilderness.backend.auth;

import com.wilderness.backend.config.AuthProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 验证码邮件发送。SMTP 未配置（spring.mail.host 为空）时返回 503，
 * 避免启动即失败——开发期可用 wilderness.auth.dev-code-log 把验证码打到日志。
 */
@Service
public class EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailService.class);

	private final ObjectProvider<JavaMailSender> mailSenderProvider;
	private final AuthProperties props;
	private final String mailHost;
	private final String mailUsername;

	public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
			AuthProperties props,
			@Value("${spring.mail.host:}") String mailHost,
			@Value("${spring.mail.username:}") String mailUsername) {
		this.mailSenderProvider = mailSenderProvider;
		this.props = props;
		this.mailHost = mailHost;
		this.mailUsername = mailUsername;
	}

	public void sendCode(String to, String code) {
		if (mailHost == null || mailHost.isBlank()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮件服务未配置");
		}
		JavaMailSender sender = mailSenderProvider.getIfAvailable();
		if (sender == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "邮件服务未配置");
		}
		try {
			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setTo(to);
			String from = props.from() == null || props.from().isBlank() ? mailUsername : props.from();
			helper.setFrom(from);
			helper.setSubject("「宇宙是旷野」登录验证码");
			helper.setText("你的验证码是 <b>" + code + "</b>，5 分钟内有效。请勿泄露给他人。", true);
			sender.send(message);
			log.info("验证码邮件已发送至 {}", to);
		} catch (Exception e) {
			log.error("验证码邮件发送失败 to={}", to, e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "邮件发送失败，请稍后重试");
		}
	}
}
