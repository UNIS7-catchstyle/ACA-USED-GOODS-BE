package com.example.app.domain.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OAuthRestClientConfig {

	private static final int CONNECT_TIMEOUT_MILLIS = 3000;
	private static final int READ_TIMEOUT_MILLIS = 5000;

	@Bean
	public RestClient oAuthRestClient() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
		requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);

		return RestClient.builder()
				.requestFactory(requestFactory)
				.build();
	}
}
