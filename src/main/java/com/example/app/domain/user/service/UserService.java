package com.example.app.domain.user.service;

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

	@Transactional(readOnly = true)
	public MeResponse getMe(Long userId) {
		User user = getActiveUserOrThrow(userId);
		int marketCount = (int) marketRepository.countByUserId(userId);
		return new MeResponse(user.getId(), user.getNickname(), user.getProfileImageUrl(), marketCount > 0, marketCount, user.getTermsAgreedAt() != null);
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
		getActiveUserOrThrow(userId);

		marketService.deleteByOwner(userId);
		scrapService.deleteAllByUser(userId);
		refreshTokenRepository.deleteByUserId(userId);

		// Re-fetch rather than reuse the User loaded above: scrapService.deleteAllByUser()
		// decrements scrap_count on every OTHER market this user had scrapped via
		// MarketRepository's clearAutomatically bulk update, which detaches everything
		// in this persistence context (including that earlier User). softDelete() on a
		// detached entity would silently never flush, so it's loaded fresh here instead.
		User user = getActiveUserOrThrow(userId);

		// Comments are deliberately left as-is (content, image_url) — only the user
		// row itself is redacted. comment.user_id keeps pointing at this (now
		// soft-deleted) row, so the author still resolves to the substituted
		// nickname without any change needed on the Comment side.
		user.softDelete();
	}

	private User getActiveUserOrThrow(Long userId) {
		return userRepository.findByIdAndDeletedAtIsNull(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
	}
}
