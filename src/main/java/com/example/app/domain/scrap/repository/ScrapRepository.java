package com.example.app.domain.scrap.repository;

import com.example.app.domain.scrap.entity.Scrap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScrapRepository extends JpaRepository<Scrap, Long> {

	void deleteByUserId(Long userId);

	// A deleted market takes any (anyone's) scrap of it with it — Scrap.market is
	// NOT NULL with no cascade, so a dangling reference would violate the FK.
	void deleteByMarketId(Long marketId);

	boolean existsByUserIdAndMarketId(Long userId, Long marketId);

	// Derived delete of a single, already-unique (user, market) row — a real DELETE,
	// not a bulk update, so no persistence-context staleness concern here. Return
	// value (0 or 1) is what makes DELETE /scrap idempotent without a separate exists check.
	long deleteByUserIdAndMarketId(Long userId, Long marketId);

	// One batched query for a whole page of markets, instead of an existsBy check per market.
	@Query("SELECT s.market.id FROM Scrap s WHERE s.user.id = :userId AND s.market.id IN :marketIds")
	List<Long> findMarketIdsByUserIdAndMarketIdIn(@Param("userId") Long userId, @Param("marketIds") List<Long> marketIds);
}
