package com.example.app.domain.comment.service;

import com.example.app.domain.comment.dto.CommentNode;
import com.example.app.domain.comment.dto.CommentRequest;
import com.example.app.domain.comment.entity.Comment;
import com.example.app.domain.comment.repository.CommentRepository;
import com.example.app.domain.image.storage.ImageStorage;
import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.domain.user.entity.User;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import com.example.app.global.transaction.AfterCommitRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

	private static final String DELETED_COMMENT_CONTENT = "삭제된 댓글입니다";

	private final CommentRepository commentRepository;
	private final MarketRepository marketRepository;
	private final UserRepository userRepository;
	private final ImageStorage imageStorage;
	private final CommentTreeAssembler commentTreeAssembler;

	@Transactional(readOnly = true)
	public List<CommentNode> getTree(Long marketId) {
		if (!marketRepository.existsById(marketId)) {
			throw new BusinessException(ErrorCode.MARKET_NOT_FOUND);
		}
		return assembleTree(marketId);
	}

	// Skips the existence check: used by MarketService.getDetail(), which has
	// already confirmed the market exists — avoids a redundant query so the detail
	// endpoint's query budget stays fixed at 3 (4 when a viewer's scrap status is
	// also fetched).
	@Transactional(readOnly = true)
	public List<CommentNode> getTreeForVerifiedMarket(Long marketId) {
		return assembleTree(marketId);
	}

	private List<CommentNode> assembleTree(Long marketId) {
		List<Comment> comments = commentRepository.findAllByMarketIdOrderByIdAsc(marketId);
		return commentTreeAssembler.assemble(comments);
	}

	@Transactional
	public CommentNode create(Long userId, Long marketId, CommentRequest request) {
		if (!marketRepository.existsById(marketId)) {
			throw new BusinessException(ErrorCode.MARKET_NOT_FOUND);
		}
		if (request.imageUrl() != null && !imageStorage.isOwnedUrl(request.imageUrl())) {
			throw new BusinessException(ErrorCode.INVALID_IMAGE_URL);
		}
		Comment parent = resolveParent(request.parentId(), marketId);

		User author = userRepository.findByIdAndDeletedAtIsNull(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

		Comment comment = Comment.builder()
				.market(marketRepository.getReferenceById(marketId))
				.user(author)
				.parent(parent)
				.content(request.content())
				.imageUrl(request.imageUrl())
				.build();
		commentRepository.save(comment);

		return new CommentNode(
				comment.getId(),
				new CommentNode.Author(author.getId(), author.getNickname()),
				comment.getContent(),
				comment.getImageUrl(),
				comment.getCreatedAt(),
				List.of());
	}

	private Comment resolveParent(Long parentId, Long marketId) {
		if (parentId == null) {
			return null;
		}
		// Missing entirely and "exists but belongs to a different market" are
		// deliberately not distinguished — both are just an invalid parent.
		return commentRepository.findById(parentId)
				.filter(comment -> comment.getMarket().getId().equals(marketId))
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARENT_COMMENT));
	}

	// Called from UserService.withdraw(). Collects this user's comment image URLs
	// before redacting, since the bulk UPDATE below nulls image_url out — losing
	// the reference is exactly why the actual file deletion has to be gathered first.
	@Transactional
	public void redactAllByUser(Long userId) {
		List<String> imageUrls = commentRepository.findImageUrlsByUserId(userId);
		commentRepository.redactByUserId(userId, DELETED_COMMENT_CONTENT);
		deleteImagesAfterCommit(imageUrls);
	}

	// Called from MarketService.deleteByOwner(). Comment rows go with the market
	// regardless of author, so their image files need the same after-commit cleanup
	// market images already get — otherwise they're orphaned on disk/S3 forever.
	@Transactional
	public void deleteAllByMarket(Long marketId) {
		List<String> imageUrls = commentRepository.findImageUrlsByMarketId(marketId);
		commentRepository.deleteByMarketId(marketId);
		deleteImagesAfterCommit(imageUrls);
	}

	private void deleteImagesAfterCommit(List<String> imageUrls) {
		if (imageUrls.isEmpty()) {
			return;
		}
		AfterCommitRunner.run(() -> imageUrls.forEach(url -> {
			try {
				imageStorage.delete(url);
			} catch (Exception e) {
				log.warn("Failed to delete comment image after commit: url={}", url, e);
			}
		}));
	}
}
