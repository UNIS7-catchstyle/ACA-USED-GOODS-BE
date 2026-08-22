package com.example.app.global.exception;

import com.example.app.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void maxUploadSizeExceededException_mapsTo400ImageSizeExceeded() {
		MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(10L * 1024 * 1024);

		ResponseEntity<ApiResponse<Void>> response = handler.handleMaxUploadSizeExceededException(exception);

		assertThat(response.getStatusCode().value()).isEqualTo(400);
		assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.IMAGE_SIZE_EXCEEDED.getCode());
	}
}
