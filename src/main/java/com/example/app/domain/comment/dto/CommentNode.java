package com.example.app.domain.comment.dto;

import java.time.LocalDateTime;
import java.util.List;

// author.nickname / content already reflect a withdrawn author's soft-delete
// substitution (User.softDelete() / CommentRepository.redactByUserId) — no special
// casing needed here. children is never null, only ever an empty list at the leaves.
public record CommentNode(
		Long id,
		Author author,
		String content,
		String imageUrl,
		LocalDateTime createdAt,
		List<CommentNode> children
) {

	public record Author(Long id, String nickname) {
	}
}
