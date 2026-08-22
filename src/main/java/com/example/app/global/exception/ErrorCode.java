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
	EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A004", "Expired token"),
	;

	// Domain codes reserve one prefix letter each: User(U), Market(M), Scrap(S), Comment(C), Image(I)

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
