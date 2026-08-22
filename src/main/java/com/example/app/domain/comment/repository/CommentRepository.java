package com.example.app.domain.comment.repository;

import com.example.app.domain.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
