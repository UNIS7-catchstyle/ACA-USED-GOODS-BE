package com.example.app.domain.user.controller;

import com.example.app.domain.user.dto.MeResponse;
import com.example.app.domain.user.service.UserService;
import com.example.app.global.response.ApiResponse;
import com.example.app.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
