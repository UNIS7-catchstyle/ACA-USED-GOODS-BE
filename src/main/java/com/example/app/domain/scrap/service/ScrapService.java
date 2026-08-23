package com.example.app.domain.scrap.service;

import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.domain.scrap.dto.ScrapResponse;
import com.example.app.domain.scrap.entity.Scrap;
import com.example.app.domain.scrap.repository.ScrapRepository;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScrapService {

	private final ScrapRepository scrapRepository;
	private final MarketRepository marketRepository;
	private final UserRepository userRepository;

	@Transactional
	public ScrapResponse scrap(Long userId, Long marketId) {
		if (!marketRepository.existsById(marketId)) {
			throw new BusinessException(ErrorCode.MARKET_NOT_FOUND);
		}
		if (scrapRepository.existsByUserIdAndMarketId(userId, marketId)) {
			throw new BusinessException(ErrorCode.SCRAP_ALREADY_EXISTS);
		}

		Scrap scrap = Scrap.builder()
				.user(userRepository.getReferenceById(userId))
				.market(marketRepository.getReferenceById(marketId))
				.build();
		try {
			// Insert first: if uk_scraps_user_id_market_id rejects a concurrent
			// double-scrap here, the count below never runs.
			scrapRepository.save(scrap);
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.SCRAP_ALREADY_EXISTS);
		}

		marketRepository.incrementScrapCount(marketId);
		return new ScrapResponse(marketRepository.findScrapCountById(marketId), true);
	}

	@Transactional
	public ScrapResponse unscrap(Long userId, Long marketId) {
		if (!marketRepository.existsById(marketId)) {
			throw new BusinessException(ErrorCode.MARKET_NOT_FOUND);
		}

		// Idempotent: deleting a scrap that doesn't exist deletes 0 rows and simply
		// reports the market's current (unchanged) count, rather than erroring.
		long deleted = scrapRepository.deleteByUserIdAndMarketId(userId, marketId);
		if (deleted > 0) {
			marketRepository.decrementScrapCount(marketId);
		}

		return new ScrapResponse(marketRepository.findScrapCountById(marketId), false);
	}
}
