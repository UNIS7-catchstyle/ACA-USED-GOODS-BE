package com.example.app.domain.comment.dto;

import java.time.LocalDateTime;
import java.util.List;

// author.nickname reflects a withdrawn author's soft-delete substitution
// (User.softDelete()) automatically — no special casing needed here. content/imageUrl
// are left exactly as originally posted; withdrawal does not touch a user's comments.
// children is never null, only ever an empty list at the leaves.
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
