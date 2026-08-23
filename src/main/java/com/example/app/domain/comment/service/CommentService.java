package com.example.app.domain.comment.service;

import com.example.app.domain.comment.dto.CommentNode;
import com.example.app.domain.comment.entity.Comment;
import com.example.app.domain.comment.repository.CommentRepository;
import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

	private final CommentRepository commentRepository;
	private final MarketRepository marketRepository;
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
}
