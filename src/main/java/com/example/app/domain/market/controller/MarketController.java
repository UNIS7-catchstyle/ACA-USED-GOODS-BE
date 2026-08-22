package com.example.app.domain.market.controller;

import com.example.app.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// TEMPORARY: placeholder only, to exercise auth-optional GET / terms-gated POST
// routing (AuthOptionalPaths, TermsAgreementInterceptor) ahead of the actual
// Market domain. Replace both methods when Market is implemented.
@RestController
@RequestMapping("/api/markets")
public class MarketController {

	@GetMapping
	public ApiResponse<List<Object>> list() {
		return ApiResponse.success(List.of());
	}

	@PostMapping
	public ResponseEntity<Void> create() {
		return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
	}
}
