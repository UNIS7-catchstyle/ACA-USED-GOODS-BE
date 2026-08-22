package com.example.app.domain.auth.client;

import com.example.app.domain.auth.entity.Provider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Test double for KakaoOAuthClient (excluded via @Profile("!test")). Always
// succeeds with a fixed providerId so auth flow tests don't call the real Kakao API.
@Component
@Profile("test")
public class FakeOAuthClient implements OAuthClient {

	public static final String FIXED_PROVIDER_ID = "fake-provider-id";

	@Override
	public Provider supports() {
		return Provider.KAKAO;
	}

	@Override
	public OAuthUserInfo getUserInfo(String accessToken) {
		return new OAuthUserInfo(FIXED_PROVIDER_ID, null);
	}
}
