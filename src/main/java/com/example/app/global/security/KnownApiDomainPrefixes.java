package com.example.app.global.security;

// Path prefixes of every top-level API domain that has real controllers mapped
// under it. Used only by JwtAuthenticationEntryPoint to distinguish "unauthenticated
// request to a route that isn't part of any real domain" (404) from "unauthenticated
// request to a route within a real domain that just isn't wired up yet, e.g. Scrap
// endpoints under /api/markets" (401). Update when a new top-level domain is added.
final class KnownApiDomainPrefixes {

	static final String[] PREFIXES = {
			"/api/auth",
			"/api/users",
			"/api/images",
			"/api/markets",
			"/api/health"
	};

	private KnownApiDomainPrefixes() {
	}
}
