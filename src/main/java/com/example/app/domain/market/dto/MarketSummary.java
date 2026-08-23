package com.example.app.domain.market.dto;

import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

// List-card shape, reused wherever a market summary shows up: GET /markets,
// and later /users/me/scraps and /users/me/commented-markets.
public record MarketSummary(
		Long id,
		String title,
		String itemCategories,
		String description,
		Category category,
		List<String> thumbnails,
		int scrapCount,
		@JsonProperty("isScrapped") boolean isScrapped,
		@JsonProperty("isClosed") boolean isClosed,
		LocalDateTime createdAt
) {

	public static MarketSummary from(Market market, boolean isScrapped, List<String> thumbnails) {
		return new MarketSummary(
				market.getId(),
				market.getTitle(),
				market.getItemCategories(),
				market.getDescription(),
				market.getCategory(),
				thumbnails,
				market.getScrapCount(),
				isScrapped,
				market.isClosed(),
				market.getCreatedAt());
	}
}
