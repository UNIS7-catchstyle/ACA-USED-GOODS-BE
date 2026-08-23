package com.example.app.domain.market.service;

import com.example.app.domain.market.dto.MarketSummary;
import com.example.app.domain.market.entity.Market;
import com.example.app.domain.market.entity.MarketImage;
import com.example.app.domain.market.repository.MarketImageRepository;
import com.example.app.domain.scrap.repository.ScrapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Shared "markets -> MarketSummary list" assembly: batched scrap-status and
// thumbnail lookups, no N+1, regardless of how many markets are passed in.
// Used by both GET /markets and GET /users/me/scraps (and, later,
// /users/me/commented-markets).
@Component
@RequiredArgsConstructor
public class MarketSummaryAssembler {

	private static final int THUMBNAIL_LIMIT = 3;

	private final ScrapRepository scrapRepository;
	private final MarketImageRepository marketImageRepository;

	public List<MarketSummary> assemble(List<Market> markets, Long viewerUserId) {
		if (markets.isEmpty()) {
			return List.of();
		}
		List<Long> marketIds = markets.stream().map(Market::getId).toList();

		Set<Long> scrappedMarketIds = viewerUserId != null
				? new HashSet<>(scrapRepository.findMarketIdsByUserIdAndMarketIdIn(viewerUserId, marketIds))
				: Set.of();
		Map<Long, List<String>> thumbnailsByMarketId = groupThumbnails(marketIds);

		return markets.stream()
				.map(market -> MarketSummary.from(
						market,
						scrappedMarketIds.contains(market.getId()),
						thumbnailsByMarketId.getOrDefault(market.getId(), List.of())))
				.toList();
	}

	private Map<Long, List<String>> groupThumbnails(List<Long> marketIds) {
		Map<Long, List<String>> grouped = marketImageRepository.findByMarketIdInOrderBySortOrderAsc(marketIds).stream()
				.collect(Collectors.groupingBy(
						image -> image.getMarket().getId(),
						LinkedHashMap::new,
						Collectors.mapping(MarketImage::getImageUrl, Collectors.toList())));
		grouped.replaceAll((id, urls) -> urls.size() <= THUMBNAIL_LIMIT ? urls : urls.subList(0, THUMBNAIL_LIMIT));
		return grouped;
	}
}
