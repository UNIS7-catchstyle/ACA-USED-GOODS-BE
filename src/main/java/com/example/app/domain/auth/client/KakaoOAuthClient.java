package com.example.app.domain.auth.client;

import com.example.app.domain.auth.entity.Provider;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Replaced by FakeOAuthClient (src/test) in the "test" profile to avoid real Kakao API calls.
@Component
@Profile("!test")
public class KakaoOAuthClient implements OAuthClient {

	private static final String USER_ME_URI = "https://kapi.kakao.com/v2/user/me";

	private final RestClient restClient;

	public KakaoOAuthClient(RestClient oAuthRestClient) {
		this.restClient = oAuthRestClient;
	}

	@Override
	public Provider supports() {
		return Provider.KAKAO;
	}

	@Override
	public OAuthUserInfo getUserInfo(String accessToken) {
		KakaoUserResponse response = fetch(accessToken);
		if (response == null) {
			throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
		}

		String profileImageUrl = response.kakaoAccount() != null && response.kakaoAccount().profile() != null
				? response.kakaoAccount().profile().profileImageUrl()
				: null;

		return new OAuthUserInfo(String.valueOf(response.id()), profileImageUrl);
	}

	private KakaoUserResponse fetch(String accessToken) {
		try {
			return restClient.get()
					.uri(USER_ME_URI)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
					.retrieve()
					.onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
						throw new BusinessException(ErrorCode.INVALID_OAUTH_TOKEN);
					})
					.onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
						throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
					})
					.body(KakaoUserResponse.class);
		} catch (BusinessException e) {
			throw e;
		} catch (RestClientException e) {
			throw new BusinessException(ErrorCode.OAUTH_PROVIDER_ERROR);
		}
	}

	private record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

		private record KakaoAccount(Profile profile) {

			private record Profile(@JsonProperty("profile_image_url") String profileImageUrl) {
			}
		}
	}
}
