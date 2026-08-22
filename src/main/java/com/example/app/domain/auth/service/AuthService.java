package com.example.app.domain.auth.service;

import com.example.app.domain.auth.client.OAuthClient;
import com.example.app.domain.auth.client.OAuthClientFactory;
import com.example.app.domain.auth.client.OAuthUserInfo;
import com.example.app.domain.auth.dto.LoginResponse;
import com.example.app.domain.auth.dto.ReissueResponse;
import com.example.app.domain.auth.entity.Provider;
import com.example.app.domain.user.entity.RefreshToken;
import com.example.app.domain.user.entity.User;
import com.example.app.domain.user.repository.RefreshTokenRepository;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.domain.user.service.NicknameGenerator;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import com.example.app.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final OAuthClientFactory oAuthClientFactory;
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final NicknameGenerator nicknameGenerator;
	private final JwtTokenProvider jwtTokenProvider;

	@Transactional
	public LoginResponse login(Provider provider, String accessToken) {
		OAuthClient oAuthClient = oAuthClientFactory.getClient(provider);
		OAuthUserInfo userInfo = oAuthClient.getUserInfo(accessToken);

		User existing = userRepository.findByProviderAndProviderId(provider, userInfo.providerId()).orElse(null);
		boolean isNewUser = existing == null;
		User user = isNewUser
				? userRepository.save(User.builder()
				.provider(provider)
				.providerId(userInfo.providerId())
				.nickname(nicknameGenerator.generate())
				.profileImageUrl(userInfo.profileImageUrl())
				.build())
				: existing;

		TokenPair tokenPair = issueTokenPair(user.getId());

		return new LoginResponse(tokenPair.accessToken(), tokenPair.refreshToken(), isNewUser, user.getTermsAgreedAt() == null);
	}

	@Transactional
	public ReissueResponse reissue(String refreshTokenValue) {
		JwtTokenProvider.TokenStatus status = jwtTokenProvider.validate(refreshTokenValue);
		if (status == JwtTokenProvider.TokenStatus.EXPIRED) {
			throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
		}
		if (status == JwtTokenProvider.TokenStatus.INVALID || !jwtTokenProvider.isRefreshToken(refreshTokenValue)) {
			throw new BusinessException(ErrorCode.INVALID_TOKEN);
		}

		Long userId = jwtTokenProvider.getUserId(refreshTokenValue);
		boolean matchesStoredToken = refreshTokenRepository.findByUserId(userId)
				.map(RefreshToken::getToken)
				.map(refreshTokenValue::equals)
				.orElse(false);
		if (!matchesStoredToken) {
			throw new BusinessException(ErrorCode.INVALID_TOKEN);
		}

		TokenPair tokenPair = issueTokenPair(userId);
		return new ReissueResponse(tokenPair.accessToken(), tokenPair.refreshToken());
	}

	@Transactional
	public void logout(Long userId) {
		refreshTokenRepository.deleteByUserId(userId);
	}

	private TokenPair issueTokenPair(Long userId) {
		String accessToken = jwtTokenProvider.generateAccessToken(userId);
		JwtTokenProvider.IssuedToken issuedRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

		RefreshToken refreshToken = refreshTokenRepository.findByUserId(userId)
				.map(existing -> {
					existing.updateToken(issuedRefreshToken.token(), issuedRefreshToken.expiresAt());
					return existing;
				})
				.orElseGet(() -> RefreshToken.builder()
						.user(userRepository.getReferenceById(userId))
						.token(issuedRefreshToken.token())
						.expiresAt(issuedRefreshToken.expiresAt())
						.build());
		refreshTokenRepository.save(refreshToken);

		return new TokenPair(accessToken, issuedRefreshToken.token());
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}
}
