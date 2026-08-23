package com.example.app.domain.comment.repository;

import com.example.app.domain.comment.entity.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	// A deleted market takes its whole comment thread with it (root + replies) —
	// otherwise those rows would keep a dangling, NOT NULL market_id.
	void deleteByMarketId(Long marketId);

	// Bulk redact instead of loading every comment into the persistence context —
	// account deletion can touch an unbounded number of a user's comments.
	// flushAutomatically: persists prior pending changes (e.g. User.softDelete())
	// before this bulk query runs. clearAutomatically: prevents later reads in the
	// same transaction from returning stale pre-redaction entities from the cache.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE Comment c SET c.content = :content, c.imageUrl = null WHERE c.user.id = :userId")
	void redactByUserId(@Param("userId") Long userId, @Param("content") String content);

	// Single query for the whole tree: author is join-fetched so CommentTreeAssembler
	// never triggers per-comment lazy loads. Ordered by id ASC so grouping by
	// parentId naturally yields id-ascending children without a separate sort.
	@Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.market.id = :marketId ORDER BY c.id ASC")
	List<Comment> findAllByMarketIdOrderByIdAsc(@Param("marketId") Long marketId);

	@Query("SELECT c.imageUrl FROM Comment c WHERE c.user.id = :userId AND c.imageUrl IS NOT NULL")
	List<String> findImageUrlsByUserId(@Param("userId") Long userId);

	@Query("SELECT c.imageUrl FROM Comment c WHERE c.market.id = :marketId AND c.imageUrl IS NOT NULL")
	List<String> findImageUrlsByMarketId(@Param("marketId") Long marketId);

	@Query("SELECT COUNT(DISTINCT c.market.id) FROM Comment c JOIN c.market m WHERE c.user.id = :userId "
			+ "AND (:excludeClosed = false OR m.isClosed = false)")
	long countDistinctMarketsByUserId(@Param("userId") Long userId, @Param("excludeClosed") boolean excludeClosed);

	// One row per market this user commented on, carrying their most recent
	// (highest-id) comment on it — that's both the display order and the cursor key.
	@Query("SELECT new com.example.app.domain.comment.repository.CommentedMarketRow(c.market.id, MAX(c.id)) "
			+ "FROM Comment c JOIN c.market m WHERE c.user.id = :userId "
			+ "AND (:excludeClosed = false OR m.isClosed = false) "
			+ "GROUP BY c.market.id "
			+ "HAVING (:cursorMaxCommentId IS NULL OR MAX(c.id) < :cursorMaxCommentId "
			+ "        OR (MAX(c.id) = :cursorMaxCommentId AND c.market.id < :cursorMarketId)) "
			+ "ORDER BY MAX(c.id) DESC, c.market.id DESC")
	List<CommentedMarketRow> findCommentedMarketPage(
			@Param("userId") Long userId,
			@Param("excludeClosed") boolean excludeClosed,
			@Param("cursorMaxCommentId") Long cursorMaxCommentId,
			@Param("cursorMarketId") Long cursorMarketId,
			Pageable pageable);
}
