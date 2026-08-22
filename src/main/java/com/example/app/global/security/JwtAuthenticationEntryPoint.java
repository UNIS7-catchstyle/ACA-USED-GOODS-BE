package com.example.app.global.security;

import com.example.app.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.List;

/**
 * Distinguishes "no token for an existing endpoint" (401) from "path does not
 * exist at all" (404) by checking whether any HandlerMapping actually resolves
 * the request, since unauthenticated requests never reach DispatcherServlet.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final List<HandlerMapping> handlerMappings;
	private final SecurityResponseWriter securityResponseWriter;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		ErrorCode errorCode = handlerExists(request) ? ErrorCode.UNAUTHORIZED : ErrorCode.NOT_FOUND;
		securityResponseWriter.write(response, errorCode);
	}

	private boolean handlerExists(HttpServletRequest request) {
		for (HandlerMapping handlerMapping : handlerMappings) {
			try {
				if (handlerMapping.getHandler(request) != null) {
					return true;
				}
			} catch (Exception ignored) {
				// this mapping cannot resolve the request; keep checking others
			}
		}
		return false;
	}
}
