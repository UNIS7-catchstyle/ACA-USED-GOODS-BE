package com.example.app.domain.comment.controller;

import com.example.app.domain.comment.dto.CommentNode;
import com.example.app.domain.comment.dto.CommentRequest;
import com.example.app.domain.comment.service.CommentService;
import com.example.app.global.response.ApiResponse;
import com.example.app.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Comment endpoints live here rather than under a Market/User controller — same
// principle as ScrapController owning its own /users/me/* endpoints by domain.
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
}
