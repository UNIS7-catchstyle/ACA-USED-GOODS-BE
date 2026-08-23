package com.example.app.domain.scrap.controller;

import com.example.app.domain.scrap.dto.ScrapResponse;
import com.example.app.domain.scrap.service.ScrapService;
import com.example.app.global.response.ApiResponse;
import com.example.app.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// Kept out of UserController (which owns /api/users/**) to avoid bloating it —
// /api/users/me/scraps lives here purely by path, not by package/class ownership.
@RestController
@RequiredArgsConstructor
public class ScrapController {

	private final ScrapService scrapService;

	@PostMapping("/api/markets/{id}/scrap")
	public ApiResponse<ScrapResponse> scrap(@CurrentUser Long userId, @PathVariable Long id) {
		return ApiResponse.success(scrapService.scrap(userId, id));
	}

	@DeleteMapping("/api/markets/{id}/scrap")
	public ApiResponse<ScrapResponse> unscrap(@CurrentUser Long userId, @PathVariable Long id) {
		return ApiResponse.success(scrapService.unscrap(userId, id));
	}
}
