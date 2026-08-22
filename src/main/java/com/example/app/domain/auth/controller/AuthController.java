package com.example.app.domain.auth.controller;

import com.example.app.domain.auth.dto.LoginRequest;
import com.example.app.domain.auth.dto.LoginResponse;
import com.example.app.domain.auth.dto.ReissueRequest;
import com.example.app.domain.auth.dto.ReissueResponse;
import com.example.app.domain.auth.entity.Provider;
import com.example.app.domain.auth.service.AuthService;
import com.example.app.global.response.ApiResponse;
import com.example.app.global.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/login/{provider}")
	public ApiResponse<LoginResponse> login(@PathVariable Provider provider, @Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(authService.login(provider, request.accessToken()));
	}

	@PostMapping("/reissue")
	public ApiResponse<ReissueResponse> reissue(@Valid @RequestBody ReissueRequest request) {
		return ApiResponse.success(authService.reissue(request.refreshToken()));
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(@CurrentUser Long userId) {
		authService.logout(userId);
		return ApiResponse.success();
	}
}
