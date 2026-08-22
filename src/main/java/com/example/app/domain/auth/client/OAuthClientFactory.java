package com.example.app.domain.auth.client;

import com.example.app.domain.auth.entity.Provider;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OAuthClientFactory {

	private final Map<Provider, OAuthClient> clients;

	public OAuthClientFactory(List<OAuthClient> oAuthClients) {
		this.clients = oAuthClients.stream()
				.collect(Collectors.toMap(OAuthClient::supports, Function.identity()));
	}

	public OAuthClient getClient(Provider provider) {
		OAuthClient client = clients.get(provider);
		if (client == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
		return client;
	}
}
