package com.example.app.domain.market.repository;

import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketRepository extends JpaRepository<Market, Long> {

	List<Market> findByUserId(Long userId);

	long countByUserId(Long userId);

	// size+1 rows requested via Pageable(0, size+1); mirrors findPage's cursor shape
	// below, scoped to one user's own markets instead of a category-wide feed.
	@Query("SELECT m FROM Market m WHERE m.user.id = :userId "
			+ "AND (:cursorCreatedAt IS NULL OR m.createdAt < :cursorCreatedAt "
			+ "     OR (m.createdAt = :cursorCreatedAt AND m.id < :cursorId)) "
			+ "ORDER BY m.createdAt DESC, m.id DESC")
	List<Market> findPageByUserId(
			@Param("userId") Long userId,
			@Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
			@Param("cursorId") Long cursorId,
			Pageable pageable);

	@Query("SELECT m FROM Market m JOIN FETCH m.user WHERE m.id = :id")
	Optional<Market> findByIdWithUser(@Param("id") Long id);

	// Doubles as an existence check for ScrapService: empty means the market
	// doesn't exist, present means it does and identifies the owner — one query
	// instead of an existsById() followed by a separate ownership lookup.
	@Query("SELECT m.user.id FROM Market m WHERE m.id = :id")
	Optional<Long> findOwnerIdById(@Param("id") Long id);

	// size+1 rows requested via Pageable(0, size+1); ORDER BY is spelled out here
	// rather than derived from Pageable's Sort, since the cursor's tie-break on id
	// needs to travel with the WHERE clause, not just the ORDER BY.
	@Query("SELECT m FROM Market m WHERE m.category = :category "
			+ "AND (:excludeClosed = false OR m.isClosed = false) "
			+ "AND (:cursorCreatedAt IS NULL OR m.createdAt < :cursorCreatedAt "
			+ "     OR (m.createdAt = :cursorCreatedAt AND m.id < :cursorId)) "
			+ "ORDER BY m.createdAt DESC, m.id DESC")
	List<Market> findPage(
			@Param("category") Category category,
			@Param("excludeClosed") boolean excludeClosed,
			@Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
			@Param("cursorId") Long cursorId,
			Pageable pageable);

	@Query("SELECT COUNT(m) FROM Market m WHERE m.category = :category AND (:excludeClosed = false OR m.isClosed = false)")
	long countByFilter(@Param("category") Category category, @Param("excludeClosed") boolean excludeClosed);

	// Atomic bulk UPDATEs — the only way scrap_count may change. Market has no
	// increaseScrapCount()/decreaseScrapCount() entity methods on purpose: a
	// read-modify-write through the entity would lose updates under concurrent
	// scraps, since two transactions could load the same pre-increment value.
	//
	// flushAutomatically=true matters here: ScrapService calls these right after a
	// Scrap insert/delete on the *same* entity manager. A plain derived delete
	// (deleteByUserIdAndMarketId) only queues entityManager.remove() — the DELETE
	// isn't issued until the next flush. clearAutomatically alone would then call
	// EntityManager.clear(), which detaches everything WITHOUT flushing first,
	// silently discarding that still-pending removal. flushAutomatically forces the
	// pending change to hit the DB before clearAutomatically wipes the context.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE Market m SET m.scrapCount = m.scrapCount + 1 WHERE m.id = :id")
	int incrementScrapCount(@Param("id") Long id);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE Market m SET m.scrapCount = m.scrapCount - 1 WHERE m.id = :id AND m.scrapCount > 0")
	int decrementScrapCount(@Param("id") Long id);

	// Scalar projection, not an entity load — always hits the DB, so it reliably
	// reflects a bulk UPDATE run earlier in the same transaction (unlike re-reading
	// an already-managed Market entity, which the persistence context would serve
	// from its stale first-level cache without clearAutomatically).
	@Query("SELECT m.scrapCount FROM Market m WHERE m.id = :id")
	Integer findScrapCountById(@Param("id") Long id);
}
