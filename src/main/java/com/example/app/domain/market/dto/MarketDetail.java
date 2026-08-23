package com.example.app.domain.market.dto;

import com.example.app.domain.comment.dto.CommentNode;
import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record MarketDetail(
		Long id,
		String title,
		String itemCategories,
		String description,
		Category category,
		List<String> images,
		int scrapCount,
		@JsonProperty("isScrapped") boolean isScrapped,
		@JsonProperty("isClosed") boolean isClosed,
		@JsonProperty("isOwner") boolean isOwner,
		Author author,
		LocalDateTime createdAt,
		List<CommentNode> comments
) {

	public static MarketDetail from(Market market, boolean isScrapped, boolean isOwner, List<String> images, List<CommentNode> comments) {
		return new MarketDetail(
				market.getId(),
				market.getTitle(),
				market.getItemCategories(),
				market.getDescription(),
				market.getCategory(),
				images,
				market.getScrapCount(),
				isScrapped,
				market.isClosed(),
				isOwner,
				new Author(market.getUser().getId(), market.getUser().getNickname()),
				market.getCreatedAt(),
				comments);
	}

	public record Author(Long id, String nickname) {
	}
}
