package com.example.app.domain.user.dto;

public record MeResponse(Long userId, String nickname, boolean needsTermsAgreement) {
}
