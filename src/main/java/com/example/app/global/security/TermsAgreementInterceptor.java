package com.example.app.global.security;

import com.example.app.domain.user.entity.User;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * Blocks two things JwtAuthenticationFilter can't, since it only trusts the JWT
 * claims and never touches the DB: requests from a soft-deleted user's (still
 * cryptographically valid) token, and requests from a user who hasn't agreed to
 * the terms yet. Runs as an MVC interceptor, not a filter, specifically because
 * it needs a DB lookup.
 */
@Component
@RequiredArgsConstructor
public class TermsAgreementInterceptor implements HandlerInterceptor {

	public static final String CURRENT_USER_REQUEST_ATTRIBUTE = "currentUser";

	// Auth-optional GET endpoints (e.g. browsing markets) never get blocked here,
	// regardless of deleted/terms status — reading is always allowed.
	private static final List<RequestMatcher> AUTH_OPTIONAL_MATCHERS = Arrays.stream(AuthOptionalPaths.GET_PATTERNS)
			.map(pattern -> new AntPathRequestMatcher(pattern, "GET"))
			.map(RequestMatcher.class::cast)
			.toList();

	private static final List<RequestMatcher> TERMS_CHECK_EXEMPT_MATCHERS = Arrays.stream(TermsCheckExemptPaths.PATTERNS)
			.map(AntPathRequestMatcher::new)
			.map(RequestMatcher.class::cast)
			.toList();

	private final UserRepository userRepository;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication != null && authentication.getPrincipal() instanceof Long userId)) {
			return true;
		}
		if (matchesAny(AUTH_OPTIONAL_MATCHERS, request)) {
			return true;
		}

		User user = userRepository.findByIdAndDeletedAtIsNull(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

		if (user.getTermsAgreedAt() == null && !matchesAny(TERMS_CHECK_EXEMPT_MATCHERS, request)) {
			throw new BusinessException(ErrorCode.TERMS_NOT_AGREED);
		}

		request.setAttribute(CURRENT_USER_REQUEST_ATTRIBUTE, user);
		return true;
	}

	private boolean matchesAny(List<RequestMatcher> matchers, HttpServletRequest request) {
		return matchers.stream().anyMatch(matcher -> matcher.matches(request));
	}
}
