package com.example.app.domain.user.controller;

import com.example.app.domain.user.dto.MeResponse;
import com.example.app.domain.user.dto.TermsAgreementRequest;
import com.example.app.domain.user.service.UserService;
import com.example.app.global.response.ApiResponse;
import com.example.app.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User", description = "내 정보, 약관 동의, 회원 탈퇴")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@Operation(summary = "내 정보 조회")
	@GetMapping("/me")
	public ApiResponse<MeResponse> me(@CurrentUser Long userId) {
		return ApiResponse.success(userService.getMe(userId));
	}

	@Operation(summary = "약관 동의 처리 (필수 약관 미동의 시 400)")
	@PostMapping("/me/terms")
	public ApiResponse<Void> agreeToTerms(@CurrentUser Long userId, @Valid @RequestBody TermsAgreementRequest request) {
		userService.agreeToTerms(userId, request);
		return ApiResponse.success();
	}

	@Operation(summary = "회원 탈퇴 (보유 마켓/스크랩 삭제, 소프트 삭제 처리, 댓글은 유지)")
	@DeleteMapping("/me")
	public ApiResponse<Void> deleteMe(@CurrentUser Long userId) {
		userService.withdraw(userId);
		return ApiResponse.success();
	}
}
