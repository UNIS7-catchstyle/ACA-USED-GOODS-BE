package com.example.app.domain.market.repository;

import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketRepository extends JpaRepository<Market, Long> {

	boolean existsByUserId(Long userId);

	Optional<Market> findByUserId(Long userId);

	@Query("SELECT m FROM Market m JOIN FETCH m.user WHERE m.id = :id")
	Optional<Market> findByIdWithUser(@Param("id") Long id);

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
}
