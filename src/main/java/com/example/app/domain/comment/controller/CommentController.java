package com.example.app.domain.comment.controller;

import com.example.app.domain.comment.dto.CommentNode;
import com.example.app.domain.comment.dto.CommentRequest;
import com.example.app.domain.comment.service.CommentService;
import com.example.app.domain.market.dto.MarketSummary;
import com.example.app.global.paging.CursorPageResponse;
import com.example.app.global.response.ApiResponse;
import com.example.app.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Both comment endpoints and /users/me/commented-markets live here, by domain
// rather than by path — same principle as ScrapController owning /users/me/scraps.
@Tag(name = "Comment", description = "마켓 댓글 (대댓글 트리)")
@Validated
@RestController
@RequiredArgsConstructor
public class CommentController {

	private final CommentService commentService;

	@Operation(summary = "마켓 댓글 트리 조회 (로그인 불필요)")
	@GetMapping("/api/markets/{id}/comments")
	public ApiResponse<List<CommentNode>> tree(@PathVariable Long id) {
		return ApiResponse.success(commentService.getTree(id));
	}

	@Operation(summary = "댓글/대댓글 작성 (parentId로 대댓글, 잘못된 parentId면 400)")
	@SecurityRequirement(name = "bearerAuth")
	@PostMapping("/api/markets/{id}/comments")
	public ResponseEntity<ApiResponse<CommentNode>> create(
			@CurrentUser Long userId, @PathVariable Long id, @Valid @RequestBody CommentRequest request) {
		CommentNode created = commentService.create(userId, id, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
	}

	@Operation(summary = "내가 댓글 단 마켓 목록 조회 (커서 페이징)")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping("/api/users/me/commented-markets")
	public ApiResponse<CursorPageResponse<MarketSummary>> commentedMarkets(
			@CurrentUser Long userId,
			@RequestParam(defaultValue = "false") boolean excludeClosed,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ApiResponse.success(commentService.getCommentedMarkets(userId, excludeClosed, cursor, size));
	}
}
