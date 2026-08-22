package com.example.app.global.security;

import com.example.app.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	// Reuses Spring Security's own request-matching logic (context path / servlet
	// path handling) instead of comparing raw getServletPath() ourselves.
	private static final List<RequestMatcher> NO_AUTH_MATCHERS = Arrays.stream(NoAuthRequiredPaths.PATTERNS)
			.map(AntPathRequestMatcher::new)
			.map(RequestMatcher.class::cast)
			.toList();

	private final JwtTokenProvider jwtTokenProvider;
	private final SecurityResponseWriter securityResponseWriter;

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return NO_AUTH_MATCHERS.stream().anyMatch(matcher -> matcher.matches(request));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String token = resolveToken(request);
		if (token == null) {
			filterChain.doFilter(request, response);
			return;
		}

		JwtTokenProvider.TokenStatus status = jwtTokenProvider.validate(token);
		if (status == JwtTokenProvider.TokenStatus.EXPIRED) {
			securityResponseWriter.write(response, ErrorCode.TOKEN_EXPIRED);
			return;
		}
		if (status == JwtTokenProvider.TokenStatus.INVALID || !jwtTokenProvider.isAccessToken(token)) {
			securityResponseWriter.write(response, ErrorCode.INVALID_TOKEN);
			return;
		}

		Long userId = jwtTokenProvider.getUserId(token);
		Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
		SecurityContextHolder.getContext().setAuthentication(authentication);
		filterChain.doFilter(request, response);
	}

	private String resolveToken(HttpServletRequest request) {
		String header = request.getHeader(AUTHORIZATION_HEADER);
		if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
			return null;
		}
		// Tolerate a Swagger "Authorize" habit of typing "Bearer <token>" into the
		// value field on top of swagger-ui's own auto-prefixing for bearer schemes.
		String token = header.substring(BEARER_PREFIX.length());
		while (token.startsWith(BEARER_PREFIX)) {
			token = token.substring(BEARER_PREFIX.length());
		}
		return token;
	}
}
