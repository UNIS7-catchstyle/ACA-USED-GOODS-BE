package com.example.app.domain.user.dto;

public record MeResponse(Long id, String nickname, String profileImageUrl, boolean hasMarket, boolean termsAgreed) {
}
