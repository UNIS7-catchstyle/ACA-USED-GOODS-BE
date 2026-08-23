package com.example.app.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// Common (C)
	INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "Invalid input value"),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "Method not allowed"),
	ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "Entity not found"),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C004", "Internal server error"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "C005", "Resource not found"),

	// Auth (A)
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "Unauthorized"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "A002", "Forbidden"),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "Invalid token"),
	TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "A004", "Token expired"),
	INVALID_OAUTH_TOKEN(HttpStatus.UNAUTHORIZED, "A005", "Invalid OAuth token"),
	OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "A006", "OAuth provider error"),

	// User (U)
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "User not found"),
	TERMS_REQUIRED_NOT_AGREED(HttpStatus.BAD_REQUEST, "U002", "Required terms not agreed"),
	TERMS_NOT_AGREED(HttpStatus.FORBIDDEN, "U003", "Terms not agreed"),

	// Image (I)
	IMAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "I001", "Image limit exceeded"),
	IMAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "I002", "Image size exceeded"),
	INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "I003", "Invalid image type"),
	IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "I004", "Image upload failed"),
	INVALID_IMAGE_URL(HttpStatus.BAD_REQUEST, "I005", "Invalid image url"),

	// Market (M)
	MARKET_REGISTRATION_CLOSED(HttpStatus.FORBIDDEN, "M001", "Market registration closed"),
	MARKET_ALREADY_EXISTS(HttpStatus.CONFLICT, "M002", "Market already exists"),
	INVALID_CURSOR(HttpStatus.BAD_REQUEST, "M003", "Invalid cursor"),
	MARKET_NOT_FOUND(HttpStatus.NOT_FOUND, "M004", "Market not found"),

	// Scrap (S)
	SCRAP_ALREADY_EXISTS(HttpStatus.CONFLICT, "S001", "Scrap already exists"),
	SELF_SCRAP_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "S002", "Self scrap not allowed"),

	// Comment (C1xx) — "C" is already taken by Common's C0xx codes above, so Comment
	// uses a three-digit C1xx range instead of a bare C0xx one to avoid colliding.
	INVALID_PARENT_COMMENT(HttpStatus.BAD_REQUEST, "C101", "Invalid parent comment"),
	;

	// Domain codes reserve one prefix letter each: User(U), Market(M), Scrap(S), Image(I).
	// Comment shares "C" with Common but is disambiguated by width (C1xx vs C0xx).

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
