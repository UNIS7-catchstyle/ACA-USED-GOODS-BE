package com.example.app.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record TermsAgreementRequest(
		@NotNull Boolean requiredAgreed,
		@NotNull Boolean marketingEmailAgreed,
		@NotNull Boolean marketingSnsAgreed
) {
}
