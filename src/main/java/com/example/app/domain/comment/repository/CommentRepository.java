package com.example.app.domain.comment.repository;

import com.example.app.domain.comment.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	// A deleted market takes its whole comment thread with it (root + replies) —
	// otherwise those rows would keep a dangling, NOT NULL market_id.
	void deleteByMarketId(Long marketId);

	// Single query for the whole tree: author is join-fetched so CommentTreeAssembler
	// never triggers per-comment lazy loads. Ordered by id ASC so grouping by
	// parentId naturally yields id-ascending children without a separate sort.
	@Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.market.id = :marketId ORDER BY c.id ASC")
	List<Comment> findAllByMarketIdOrderByIdAsc(@Param("marketId") Long marketId);

	@Query("SELECT c.imageUrl FROM Comment c WHERE c.market.id = :marketId AND c.imageUrl IS NOT NULL")
	List<String> findImageUrlsByMarketId(@Param("marketId") Long marketId);
}
