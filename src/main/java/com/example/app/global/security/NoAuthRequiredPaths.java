package com.example.app.global.security;

// Endpoints that need no token at all — shared by SecurityConfig (permitAll) and
// JwtAuthenticationFilter (shouldNotFilter), so a garbage/expired Authorization
// header on these paths never blocks the request with a 401.
public final class NoAuthRequiredPaths {

	public static final String[] PATTERNS = {
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/v3/api-docs/**",
			"/api/health",
			"/actuator/health",
			"/api/auth/login/**",
			"/api/auth/reissue",
			"/api/markets/registration-status",
			"/uploads/**"
	};

	private NoAuthRequiredPaths() {
	}
}
