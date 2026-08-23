package com.example.app.domain.comment.controller;

import com.example.app.domain.comment.dto.CommentNode;
import com.example.app.domain.comment.dto.CommentRequest;
import com.example.app.domain.comment.service.CommentService;
import com.example.app.domain.market.dto.MarketSummary;
import com.example.app.global.paging.CursorPageResponse;
import com.example.app.global.response.ApiResponse;
import com.example.app.global.security.CurrentUser;
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
@Validated
@RestController
@RequiredArgsConstructor
public class CommentController {

	private final CommentService commentService;

	@GetMapping("/api/markets/{id}/comments")
	public ApiResponse<List<CommentNode>> tree(@PathVariable Long id) {
		return ApiResponse.success(commentService.getTree(id));
	}

	@PostMapping("/api/markets/{id}/comments")
	public ResponseEntity<ApiResponse<CommentNode>> create(
			@CurrentUser Long userId, @PathVariable Long id, @Valid @RequestBody CommentRequest request) {
		CommentNode created = commentService.create(userId, id, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
	}

	@GetMapping("/api/users/me/commented-markets")
	public ApiResponse<CursorPageResponse<MarketSummary>> commentedMarkets(
			@CurrentUser Long userId,
			@RequestParam(defaultValue = "false") boolean excludeClosed,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ApiResponse.success(commentService.getCommentedMarkets(userId, excludeClosed, cursor, size));
	}
}
