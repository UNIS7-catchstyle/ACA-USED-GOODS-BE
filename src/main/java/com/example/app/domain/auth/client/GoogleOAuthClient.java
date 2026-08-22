package com.example.app.domain.auth.client;

import com.example.app.domain.auth.entity.Provider;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GoogleOAuthClient implements OAuthClient {

	private static final String USER_INFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

	private final RestClient restClient;

	public GoogleOAuthClient(RestClient oAuthRestClient) {
		this.restClient = oAuthRestClient;
	}

	@Override
	public Provider supports() {
		return Provider.GOOGLE;
	}

	@Override
	public OAuthUserInfo getUserInfo(String accessToken) {
		GoogleUserResponse response = fetch(accessToken);
		if (response == null) {
			throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
		}
		return new OAuthUserInfo(response.sub(), response.picture());
	}

	private GoogleUserResponse fetch(String accessToken) {
		try {
			return restClient.get()
					.uri(USER_INFO_URI)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
					.retrieve()
					.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
						throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN);
					})
					.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
						throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
					})
					.body(GoogleUserResponse.class);
		} catch (BusinessException e) {
			throw e;
		} catch (RestClientException e) {
			throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
		}
	}

	private record GoogleUserResponse(String sub, String picture) {
	}
}
