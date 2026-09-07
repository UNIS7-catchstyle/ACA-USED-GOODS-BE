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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// No class-level @RequestMapping: /api/users/me/markets lives here by domain (same
// principle as ScrapController/CommentController owning their own /users/me/*
// endpoints), and a shared "/api/markets" prefix would wrongly get prepended to it.
@Tag(name = "Market", description = "마켓 등록/조회/수정")
@Validated
@RestController
@RequiredArgsConstructor
public class MarketController {

	private final MarketService marketService;

	@Operation(summary = "마켓 목록 조회 (커서 페이징, category 필수) (로그인 시 isScrapped 반영)")
	@GetMapping("/api/markets")
	public ApiResponse<CursorPageResponse<MarketSummary>> list(
			@CurrentUser(required = false) Long userId,
			@RequestParam Category category,
			@RequestParam(defaultValue = "false") boolean excludeClosed,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ApiResponse.success(marketService.getMarkets(userId, category, excludeClosed, cursor, size));
	}

	@Operation(summary = "마켓 등록 (유저당 여러 개 가능, 등록 마감 시 403)")
	@SecurityRequirement(name = "bearerAuth")
	@PostMapping("/api/markets")
	public ResponseEntity<ApiResponse<MarketIdResponse>> create(@CurrentUser Long userId, @Valid @RequestBody MarketRequest request) {
		MarketIdResponse response = marketService.register(userId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@Operation(summary = "마켓 등록 가능 여부 조회")
	@GetMapping("/api/markets/registration-status")
	public ApiResponse<RegistrationStatusResponse> registrationStatus() {
		return ApiResponse.success(marketService.getRegistrationStatus());
	}

	@Operation(summary = "마켓 상세 조회 (로그인 시 isScrapped 반영, 존재하지 않으면 404)")
	@GetMapping("/api/markets/{id}")
	public ApiResponse<MarketDetail> detail(@CurrentUser(required = false) Long userId, @PathVariable Long id) {
		return ApiResponse.success(marketService.getDetail(userId, id));
	}

	@Operation(summary = "마켓 수정 (작성자 본인만 가능, 아니면 403)")
	@SecurityRequirement(name = "bearerAuth")
	@PutMapping("/api/markets/{id}")
	public ApiResponse<MarketDetail> update(@CurrentUser Long userId, @PathVariable Long id, @Valid @RequestBody MarketRequest request) {
		return ApiResponse.success(marketService.update(userId, id, request));
	}

	@Operation(summary = "내가 등록한 마켓 목록 조회 (커서 페이징)")
	@SecurityRequirement(name = "bearerAuth")
	@GetMapping("/api/users/me/markets")
	public ApiResponse<CursorPageResponse<MarketSummary>> myMarkets(
			@CurrentUser Long userId,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ApiResponse.success(marketService.getMyMarkets(userId, cursor, size));
	}
}
