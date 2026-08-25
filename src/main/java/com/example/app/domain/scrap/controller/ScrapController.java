package com.example.app.domain.scrap.controller;

import com.example.app.domain.market.dto.MarketSummary;
import com.example.app.domain.market.entity.Category;
import com.example.app.domain.scrap.dto.ScrapResponse;
import com.example.app.domain.scrap.service.ScrapService;
import com.example.app.global.paging.CursorPageResponse;
import com.example.app.global.response.ApiResponse;
import com.example.app.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Kept out of UserController (which owns /api/users/**) to avoid bloating it —
// /api/users/me/scraps lives here purely by path, not by package/class ownership.
@Tag(name = "Scrap", description = "마켓 스크랩(찜)")
@SecurityRequirement(name = "bearerAuth")
@Validated
@RestController
@RequiredArgsConstructor
public class ScrapController {

	private final ScrapService scrapService;

	@Operation(summary = "마켓 스크랩 (본인 마켓 스크랩 불가 400, 중복 스크랩 시 409)")
	@PostMapping("/api/markets/{id}/scrap")
	public ApiResponse<ScrapResponse> scrap(@CurrentUser Long userId, @PathVariable Long id) {
		return ApiResponse.success(scrapService.scrap(userId, id));
	}

	@Operation(summary = "마켓 스크랩 해제")
	@DeleteMapping("/api/markets/{id}/scrap")
	public ApiResponse<ScrapResponse> unscrap(@CurrentUser Long userId, @PathVariable Long id) {
		return ApiResponse.success(scrapService.unscrap(userId, id));
	}

	@Operation(summary = "내가 스크랩한 마켓 목록 조회 (커서 페이징)")
	@GetMapping("/api/users/me/scraps")
	public ApiResponse<CursorPageResponse<MarketSummary>> myScraps(
			@CurrentUser Long userId,
			@RequestParam(required = false) Category category,
			@RequestParam(defaultValue = "false") boolean excludeClosed,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
		return ApiResponse.success(scrapService.getMyScraps(userId, category, excludeClosed, cursor, size));
	}
}
