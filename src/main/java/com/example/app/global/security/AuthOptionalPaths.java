package com.example.app.global.security;

// GET-only browse endpoints: no token required, but a valid token still populates
// the principal. Shared by SecurityConfig (permitAll) and TermsAgreementInterceptor
// (never blocked by deleted-user/terms checks — reading is always allowed).
final class AuthOptionalPaths {

	static final String[] GET_PATTERNS = {
			"/api/markets",
			"/api/markets/*"
	};

	private AuthOptionalPaths() {
	}
}
