package com.example.app.domain.auth.dto;

public record LoginResponse(String accessToken, String refreshToken, boolean isNewUser, boolean needsTermsAgreement) {
}
