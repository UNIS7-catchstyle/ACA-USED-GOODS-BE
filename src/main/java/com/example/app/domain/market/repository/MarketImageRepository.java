package com.example.app.domain.market.repository;

import com.example.app.domain.market.entity.MarketImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketImageRepository extends JpaRepository<MarketImage, Long> {

	void deleteByMarketId(Long marketId);

	// Single batched query for a whole page of markets — callers (thumbnails,
	// detail images) group/trim the flat list themselves, avoiding N+1.
	List<MarketImage> findByMarketIdInOrderBySortOrderAsc(List<Long> marketIds);
}
