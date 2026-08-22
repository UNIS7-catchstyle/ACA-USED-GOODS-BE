package com.example.app.domain.user.controller;

import com.example.app.domain.user.dto.MeResponse;
import com.example.app.domain.user.dto.TermsAgreementRequest;
import com.example.app.domain.user.service.UserService;
import com.example.app.global.response.ApiResponse;
import com.example.app.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping("/me")
	public ApiResponse<MeResponse> me(@CurrentUser Long userId) {
		return ApiResponse.success(userService.getMe(userId));
	}

	@PostMapping("/me/terms")
	public ApiResponse<Void> agreeToTerms(@CurrentUser Long userId, @Valid @RequestBody TermsAgreementRequest request) {
		userService.agreeToTerms(userId, request);
		return ApiResponse.success();
	}

	@DeleteMapping("/me")
	public ApiResponse<Void> deleteMe(@CurrentUser Long userId) {
		userService.withdraw(userId);
		return ApiResponse.success();
	}
}
