package com.example.app.global.security;

import com.example.app.global.exception.ErrorCode;
import com.example.app.global.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Shared by JwtAuthenticationFilter/EntryPoint/AccessDeniedHandler, which all run
// outside DispatcherServlet and so can't rely on GlobalExceptionHandler.
@Component
@RequiredArgsConstructor
public class SecurityResponseWriter {

	private final ObjectMapper objectMapper;

	public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getHttpStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(errorCode)));
	}
}
