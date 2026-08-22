package com.example.app.domain.scrap.repository;

import com.example.app.domain.scrap.entity.Scrap;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {

	void deleteByUserId(Long userId);

	// A deleted market takes any (anyone's) scrap of it with it — Scrap.market is
	// NOT NULL with no cascade, so a dangling reference would violate the FK.
	void deleteByMarketId(Long marketId);
}
