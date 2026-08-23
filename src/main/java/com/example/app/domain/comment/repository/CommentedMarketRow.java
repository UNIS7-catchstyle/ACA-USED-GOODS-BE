package com.example.app.domain.comment.repository;

// JPQL constructor-expression projection for the "markets I commented on" page
// query: one row per market, carrying the market id and this user's most recent
// comment id on it (the sort/cursor key).
public record CommentedMarketRow(Long marketId, Long maxCommentId) {
}
