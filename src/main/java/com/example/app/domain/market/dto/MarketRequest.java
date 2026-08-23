package com.example.app.domain.market.dto;

import com.example.app.domain.market.entity.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// Shared by POST and PUT /markets. isClosed is ignored on POST (always created as
// open) and only takes effect on PUT — MarketService enforces that, not this DTO.
public record MarketRequest(
		@NotNull Category category,
		@NotBlank @Size(max = 100) String title,
		@NotBlank @Size(max = 200) String itemCategories,
		@NotBlank @Size(max = 2000) String description,
		@Size(max = 20) List<String> imageUrls,
		Boolean isClosed
) {

	public List<String> imageUrlsOrEmpty() {
		return imageUrls == null ? List.of() : imageUrls;
	}

	public boolean isClosedOrFalse() {
		return Boolean.TRUE.equals(isClosed);
	}
}
