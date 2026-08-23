package com.example.app.domain.market.controller;

import com.example.app.domain.market.dto.MarketDetail;
import com.example.app.domain.market.dto.MarketIdResponse;
import com.example.app.domain.market.dto.MarketRequest;
import com.example.app.domain.market.dto.MarketSummary;
import com.example.app.domain.market.dto.RegistrationStatusResponse;
import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.service.MarketService;
import com.example.app.global.paging.CursorPageResponse;
import com.example.app.global.response.ApiResponse;
import com.example.app.global.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/markets")
@RequiredArgsConstructor
public class MarketController {

	private final MarketService marketService;

	@GetMapping
	public ApiResponse<CursorPageResponse<MarketSummary>> list(
			@CurrentUser(required = false) Long userId,
			@RequestParam Category category,
			@RequestParam(defaultValue = "false") boolean excludeClosed,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ApiResponse.success(marketService.getMarkets(userId, category, excludeClosed, cursor, size));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<MarketIdResponse>> create(@CurrentUser Long userId, @Valid @RequestBody MarketRequest request) {
		MarketIdResponse response = marketService.register(userId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@GetMapping("/registration-status")
	public ApiResponse<RegistrationStatusResponse> registrationStatus() {
		return ApiResponse.success(marketService.getRegistrationStatus());
	}

	@GetMapping("/{id}")
	public ApiResponse<MarketDetail> detail(@CurrentUser(required = false) Long userId, @PathVariable Long id) {
		return ApiResponse.success(marketService.getDetail(userId, id));
	}

	@PutMapping("/{id}")
	public ApiResponse<MarketDetail> update(@CurrentUser Long userId, @PathVariable Long id, @Valid @RequestBody MarketRequest request) {
		return ApiResponse.success(marketService.update(userId, id, request));
	}
}
