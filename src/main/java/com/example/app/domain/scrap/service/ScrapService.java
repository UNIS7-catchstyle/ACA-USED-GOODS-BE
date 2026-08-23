package com.example.app.domain.scrap.service;

import com.example.app.domain.market.dto.MarketSummary;
import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.domain.market.service.MarketSummaryAssembler;
import com.example.app.domain.scrap.dto.ScrapResponse;
import com.example.app.domain.scrap.entity.Scrap;
import com.example.app.domain.scrap.repository.ScrapRepository;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import com.example.app.global.paging.Cursor;
import com.example.app.global.paging.CursorPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScrapService {

	private final ScrapRepository scrapRepository;
	private final MarketRepository marketRepository;
	private final UserRepository userRepository;
	private final MarketSummaryAssembler marketSummaryAssembler;

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

	@Transactional(readOnly = true)
	public CursorPageResponse<MarketSummary> getMyScraps(Long userId, Category category, boolean excludeClosed, String cursorParam, int size) {
		LocalDateTime cursorCreatedAt = null;
		Long cursorId = null;
		if (StringUtils.hasText(cursorParam)) {
			try {
				Cursor cursor = Cursor.decode(cursorParam);
				cursorCreatedAt = cursor.createdAt();
				cursorId = cursor.id();
			} catch (IllegalArgumentException e) {
				throw new BusinessException(ErrorCode.INVALID_CURSOR);
			}
		}

		long totalCount = scrapRepository.countByUserIdAndFilter(userId, category, excludeClosed);
		List<Scrap> fetched = scrapRepository.findPageByUserId(userId, category, excludeClosed, cursorCreatedAt, cursorId, PageRequest.of(0, size + 1));

		boolean hasNext = fetched.size() > size;
		List<Scrap> pageScraps = hasNext ? fetched.subList(0, size) : fetched;
		List<Market> markets = pageScraps.stream().map(Scrap::getMarket).toList();

		// Always the honest batch scrap-lookup via the assembler (every item will
		// come back isScrapped=true) rather than a special "assume all scrapped"
		// path — one less thing to keep in sync if the assembler's logic changes.
		List<MarketSummary> items = marketSummaryAssembler.assemble(markets, userId);

		String nextCursor = hasNext ? cursorOf(pageScraps.get(pageScraps.size() - 1)).encode() : null;
		return new CursorPageResponse<>(totalCount, items, nextCursor, hasNext);
	}

	private Cursor cursorOf(Scrap scrap) {
		return new Cursor(scrap.getCreatedAt(), scrap.getId());
	}
}
