package com.example.app.domain.comment.dto;

// Placeholder shape for MarketDetail.comments ahead of the Comment domain: only
// `id` for now. MarketService always returns an empty list of these; the Comment
// domain implementation is expected to grow this record (content, author, replies, ...)
// and populate MarketDetail.comments for real.
public record CommentNode(Long id) {
}
