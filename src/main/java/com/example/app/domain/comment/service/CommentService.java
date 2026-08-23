package com.example.app.domain.comment.service;

import com.example.app.domain.comment.dto.CommentNode;
import com.example.app.domain.comment.dto.CommentRequest;
import com.example.app.domain.comment.entity.Comment;
import com.example.app.domain.comment.repository.CommentRepository;
import com.example.app.domain.comment.repository.CommentedMarketRow;
import com.example.app.domain.image.storage.ImageStorage;
import com.example.app.domain.market.dto.MarketSummary;
import com.example.app.domain.market.entity.Market;
import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.domain.market.service.MarketSummaryAssembler;
import com.example.app.domain.user.entity.User;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import com.example.app.global.paging.Cursor;
import com.example.app.global.paging.CursorPageResponse;
import com.example.app.global.transaction.AfterCommitRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

	private final CommentRepository commentRepository;
	private final MarketRepository marketRepository;
	private final UserRepository userRepository;
	private final ImageStorage imageStorage;
	private final CommentTreeAssembler commentTreeAssembler;
	private final MarketSummaryAssembler marketSummaryAssembler;

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

	// Called from MarketService.deleteByOwner(). Comment rows go with the market
	// regardless of author, so their image files need the same after-commit cleanup
	// market images already get — otherwise they're orphaned on disk/S3 forever.
	@Transactional
	public void deleteAllByMarket(Long marketId) {
		List<String> imageUrls = commentRepository.findImageUrlsByMarketId(marketId);
		commentRepository.deleteByMarketId(marketId);
		deleteImagesAfterCommit(imageUrls);
	}

	@Transactional(readOnly = true)
	public CursorPageResponse<MarketSummary> getCommentedMarkets(Long userId, boolean excludeClosed, String cursorParam, int size) {
		Long cursorMaxCommentId = null;
		Long cursorMarketId = null;
		if (StringUtils.hasText(cursorParam)) {
			try {
				Cursor cursor = Cursor.decode(cursorParam);
				cursorMaxCommentId = cursor.key1();
				cursorMarketId = cursor.key2();
			} catch (IllegalArgumentException e) {
				throw new BusinessException(ErrorCode.INVALID_CURSOR);
			}
		}

		long totalCount = commentRepository.countDistinctMarketsByUserId(userId, excludeClosed);
		List<CommentedMarketRow> fetched = commentRepository.findCommentedMarketPage(
				userId, excludeClosed, cursorMaxCommentId, cursorMarketId, PageRequest.of(0, size + 1));

		boolean hasNext = fetched.size() > size;
		List<CommentedMarketRow> pageRows = hasNext ? fetched.subList(0, size) : fetched;
		List<Long> marketIds = pageRows.stream().map(CommentedMarketRow::marketId).toList();

		Map<Long, Market> marketsById = marketRepository.findAllById(marketIds).stream()
				.collect(Collectors.toMap(Market::getId, market -> market));
		// findAllById doesn't preserve input order — re-sort back into the page's
		// "most recently commented on" order before handing off to the assembler.
		List<Market> orderedMarkets = marketIds.stream().map(marketsById::get).toList();

		List<MarketSummary> items = marketSummaryAssembler.assemble(orderedMarkets, userId);

		String nextCursor = hasNext ? cursorOf(pageRows.get(pageRows.size() - 1)).encode() : null;
		return new CursorPageResponse<>(totalCount, items, nextCursor, hasNext);
	}

	private Cursor cursorOf(CommentedMarketRow row) {
		return new Cursor(row.maxCommentId(), row.marketId());
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
