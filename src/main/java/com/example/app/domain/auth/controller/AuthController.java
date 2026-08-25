package com.example.app.domain.auth.controller;

import com.example.app.domain.auth.dto.LoginRequest;
import com.example.app.domain.auth.dto.LoginResponse;
import com.example.app.domain.auth.dto.ReissueRequest;
import com.example.app.domain.auth.dto.ReissueResponse;
import com.example.app.domain.auth.entity.Provider;
import com.example.app.domain.auth.service.AuthService;
import com.example.app.global.response.ApiResponse;
import com.example.app.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "로그인, 토큰 재발급, 로그아웃")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@Operation(summary = "OAuth 로그인 (KAKAO/GOOGLE, 최초 로그인 시 자동 회원가입)")
	@PostMapping("/login/{provider}")
	public ApiResponse<LoginResponse> login(@PathVariable Provider provider, @Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(authService.login(provider, request.accessToken()));
	}

	@Operation(summary = "Access/Refresh 토큰 재발급 (Refresh 토큰 만료/무효 시 401)")
	@PostMapping("/reissue")
	public ApiResponse<ReissueResponse> reissue(@Valid @RequestBody ReissueRequest request) {
		return ApiResponse.success(authService.reissue(request.refreshToken()));
	}

	@Operation(summary = "로그아웃 (서버에 저장된 Refresh 토큰 무효화)")
	@SecurityRequirement(name = "bearerAuth")
	@PostMapping("/logout")
	public ApiResponse<Void> logout(@CurrentUser Long userId) {
		authService.logout(userId);
		return ApiResponse.success();
	}
}
