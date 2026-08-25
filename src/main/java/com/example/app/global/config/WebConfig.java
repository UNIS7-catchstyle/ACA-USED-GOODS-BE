package com.example.app.global.config;

import com.example.app.global.security.CurrentUserArgumentResolver;
import com.example.app.global.security.NoAuthRequiredPaths;
import com.example.app.global.security.TermsAgreementInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final CurrentUserArgumentResolver currentUserArgumentResolver;
	private final TermsAgreementInterceptor termsAgreementInterceptor;

	// Defined as a CorsConfigurationSource bean (rather than addCorsMappings) so
	// SecurityConfig's cors(Customizer.withDefaults()) picks it up directly and a
	// CorsFilter runs ahead of the authorization filter — otherwise a preflight
	// OPTIONS request to an authenticated path 401s before CORS headers are added.
	@Bean
	public CorsConfigurationSource corsConfigurationSource(@Value("${cors.allowed-origins}") String allowedOrigins) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		// Authorization is a header, not a cookie — no credentialed CORS needed, and
		// allowCredentials(true) is disallowed together with a "*" origin anyway.
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

	// Bean Validation messages resolve via LocaleContextHolder.getLocale(), which by
	// default follows the request's Accept-Language header (or the JVM default locale
	// if absent) — fixing it to Korean keeps validation messages consistent across
	// environments regardless of client headers or server locale.
	@Bean
	public LocaleResolver localeResolver() {
		return new FixedLocaleResolver(Locale.KOREAN);
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(currentUserArgumentResolver);
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(termsAgreementInterceptor)
				.addPathPatterns("/api/**")
				.excludePathPatterns(NoAuthRequiredPaths.PATTERNS);
	}
}
