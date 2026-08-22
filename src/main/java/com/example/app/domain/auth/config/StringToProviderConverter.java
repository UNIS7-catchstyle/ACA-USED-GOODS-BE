package com.example.app.domain.auth.config;

import com.example.app.domain.auth.entity.Provider;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

// Registered automatically with Spring MVC's ConversionService as a Converter bean.
// Lets /api/auth/login/{provider} accept lowercase path values (kakao, google).
@Component
public class StringToProviderConverter implements Converter<String, Provider> {

	@Override
	public Provider convert(@NonNull String source) {
		return Provider.valueOf(source.toUpperCase());
	}
}
