package com.example.app.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
		@NotBlank @Size(max = 1000) String content,
		String imageUrl,
		Long parentId
) {
}
