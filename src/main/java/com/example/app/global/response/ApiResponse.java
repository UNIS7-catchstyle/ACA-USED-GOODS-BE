package com.example.app.global.response;

import com.example.app.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class ApiResponse<T> {

	private final boolean success;
	private final String code;
	private final String message;
	private final T data;

	private ApiResponse(boolean success, String code, String message, T data) {
		this.success = success;
		this.code = code;
		this.message = message;
		this.data = data;
	}

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, "SUCCESS", "OK", data);
	}

	public static ApiResponse<Void> success() {
		return new ApiResponse<>(true, "SUCCESS", "OK", null);
	}

	public static <T> ApiResponse<T> error(ErrorCode errorCode) {
		return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), null);
	}

	public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
		return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), data);
	}

	public static <T> ApiResponse<T> errorWithMessage(ErrorCode errorCode, String message) {
		return new ApiResponse<>(false, errorCode.getCode(), message, null);
	}
}
