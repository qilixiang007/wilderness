package com.wilderness.backend.config;

import com.wilderness.backend.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 把异常统一包装为 {@link ApiResponse}，保证错误响应与成功响应同形。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiResponse<Void>> handleStatus(ResponseStatusException ex) {
		HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
		return ResponseEntity.status(status).body(ApiResponse.fail(ex.getReason()));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoResource() {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("Resource not found"));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.fail("Missing required parameter: " + ex.getParameterName()));
	}

	/** @Valid 请求体校验失败 → 400，带出具体字段错误（否则会落到通用 Exception 变成 500）。 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(f -> f.getField() + ": " + f.getDefaultMessage())
				.orElse("Validation failed");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(message));
	}

	/**
	 * 业务校验失败（如「不支持的文件类型」「上传文件为空」）→ 400，并把具体原因带给前端。
	 * 不加这个 handler 的话会落到下面的通用兜底，变成 500 + "Internal server error"，
	 * 真实原因被吞掉——知识库上传的逐文件失败提示全靠这条。
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
		String message = (ex.getMessage() == null || ex.getMessage().isBlank()) ? "请求参数不合法" : ex.getMessage();
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(message));
	}

	/** 上传体积超过 multipart 上限 → 400（同样避免落到通用兜底变成 500）。 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge() {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.fail("上传文件体积超出限制，请减少文件数量或压缩后重试"));
	}

	/** 请求体 JSON 解析失败（如字段类型不匹配）→ 400。 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadable() {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail("请求体格式错误"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("Internal server error"));
	}
}
