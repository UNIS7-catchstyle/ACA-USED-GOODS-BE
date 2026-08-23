package com.example.app.domain.user.service;

import com.example.app.domain.comment.service.CommentService;
import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.domain.market.service.MarketService;
import com.example.app.domain.scrap.service.ScrapService;
import com.example.app.domain.user.dto.MeResponse;
import com.example.app.domain.user.dto.TermsAgreementRequest;
import com.example.app.domain.user.entity.User;
import com.example.app.domain.user.repository.RefreshTokenRepository;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final MarketRepository marketRepository;
	private final MarketService marketService;
	private final ScrapService scrapService;
	private final RefreshTokenRepository refreshTokenRepository;
	private final CommentService commentService;

	@Transactional(readOnly = true)
	public MeResponse getMe(Long userId) {
		User user = getActiveUserOrThrow(userId);
		boolean hasMarket = marketRepository.existsByUserId(userId);
		return new MeResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), hasMarket, user.getTermsAgreedAt() != null);
	}

	@Transactional
	public void agreeToTerms(Long userId, TermsAgreementRequest request) {
		if (!Boolean.TRUE.equals(request.requiredAgreed())) {
			throw new BusinessException(ErrorCode.TERMS_REQUIRED_NOT_AGREED);
		}
		User user = getActiveUserOrThrow(userId);
		user.agreeToTerms(request.marketingEmailAgreed(), request.marketingSnsAgreed());
	}

	@Transactional
	public void withdraw(Long userId) {
		User user = getActiveUserOrThrow(userId);

		marketService.deleteByOwner(userId);
		scrapService.deleteAllByUser(userId);
		refreshTokenRepository.deleteByUserId(userId);

		// Must run before the redact call below: its flushAutomatically persists this
		// mutation, and its clearAutomatically would otherwise detach `user` first,
		// silently dropping the change.
		user.softDelete();

		commentService.redactAllByUser(userId);
	}

	private User getActiveUserOrThrow(Long userId) {
		return userRepository.findByIdAndDeletedAtIsNull(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}
}
