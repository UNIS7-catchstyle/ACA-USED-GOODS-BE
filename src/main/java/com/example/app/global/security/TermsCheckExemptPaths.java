package com.example.app.global.security;

// Authenticated endpoints that must work even before the terms are agreed to.
// Shared by TermsAgreementInterceptor's own path check (NOT Spring's
// excludePathPatterns — that would also skip the deleted-user check these paths
// still need; see TermsAgreementInterceptor).
final class TermsCheckExemptPaths {

	static final String[] PATTERNS = {
			"/api/auth/**",
			"/api/users/me",
			"/api/users/me/terms"
	};

	private TermsCheckExemptPaths() {
	}
}
