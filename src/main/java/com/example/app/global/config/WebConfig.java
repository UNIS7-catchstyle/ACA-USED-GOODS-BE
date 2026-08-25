package com.example.app.global.config;

import com.example.app.global.security.CurrentUserArgumentResolver;
import com.example.app.global.security.NoAuthRequiredPaths;
import com.example.app.global.security.TermsAgreementInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.List;
import java.util.Locale;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	private final CurrentUserArgumentResolver currentUserArgumentResolver;
	private final TermsAgreementInterceptor termsAgreementInterceptor;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOriginPatterns("*")
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.allowCredentials(true);
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
