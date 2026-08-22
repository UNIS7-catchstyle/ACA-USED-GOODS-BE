package com.example.app.domain.auth.client;

import com.example.app.domain.auth.entity.Provider;

public interface OAuthClient {

	Provider supports();

	OAuthUserInfo getUserInfo(String accessToken);
}
