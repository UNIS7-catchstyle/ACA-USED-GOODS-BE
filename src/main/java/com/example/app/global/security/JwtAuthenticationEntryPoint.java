package com.example.app.global.security;

import com.example.app.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;

// Distinguishes "no token for a route within a known API domain" (401) — including
// a domain's not-yet-implemented sub-routes, e.g. Scrap endpoints under
// /api/markets — from "path isn't part of any real API domain at all" (404).
// Relies on SecurityConfig disabling anonymous auth: otherwise the default
// anonymous principal satisfies anyRequest().authenticated() and this entry point
// never runs, letting an unmapped path fall through to DispatcherServlet's own 404.
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final SecurityResponseWriter securityResponseWriter;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		ErrorCode errorCode = withinKnownDomain(request) ? ErrorCode.UNAUTHORIZED : ErrorCode.NOT_FOUND;
		securityResponseWriter.write(response, errorCode);
	}

	private boolean withinKnownDomain(HttpServletRequest request) {
		String path = request.getRequestURI();
		return Arrays.stream(KnownApiDomainPrefixes.PREFIXES).anyMatch(path::startsWith);
	}
}
