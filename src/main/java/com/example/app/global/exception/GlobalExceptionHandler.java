package com.example.app.global.exception;

import com.example.app.global.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
		log.warn("BusinessException: {}", e.getMessage());
		ErrorCode errorCode = e.getErrorCode();
		return ResponseEntity.status(errorCode.getHttpStatus())
				.body(ApiResponse.errorWithMessage(errorCode, e.getMessage()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
		return ResponseEntity.status(ErrorCode.IMAGE_SIZE_EXCEEDED.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.IMAGE_SIZE_EXCEEDED));
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
	public ResponseEntity<ApiResponse<List<FieldErrorDetail>>> handleBindException(BindException e) {
		List<FieldErrorDetail> details = e.getBindingResult().getFieldErrors().stream()
				.map(fieldError -> new FieldErrorDetail(fieldError.getField(), fieldError.getDefaultMessage()))
				.toList();
		return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, details));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e) {
		return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE));
	}

	// Spring MVC's native method-parameter validation (@Min/@Max directly on a
	// @RequestParam) raises this instead of ConstraintViolationException.
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiResponse<List<FieldErrorDetail>>> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
		List<FieldErrorDetail> details = e.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> new FieldErrorDetail(result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
				.toList();
		return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, details));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
		return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
		return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
		return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
		return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED));
	}

	@ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
	public ResponseEntity<ApiResponse<Void>> handleNotFoundException(Exception e) {
		return ResponseEntity.status(ErrorCode.NOT_FOUND.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.NOT_FOUND));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
		return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.FORBIDDEN));
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException e) {
		return ResponseEntity.status(ErrorCode.UNAUTHORIZED.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.UNAUTHORIZED));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		log.error("Unhandled exception", e);
		return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus())
				.body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR));
	}
}
