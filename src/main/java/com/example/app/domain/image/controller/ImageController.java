package com.example.app.domain.image.controller;

import com.example.app.domain.image.dto.ImageUploadResponse;
import com.example.app.domain.image.service.ImageService;
import com.example.app.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Image", description = "마켓/댓글에서 쓸 이미지 업로드")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

	private final ImageService imageService;

	@Operation(summary = "이미지 업로드 (여러 장, 개수/용량/타입 초과 시 400)")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<ImageUploadResponse> upload(
			@RequestPart(value = "files", required = false) List<MultipartFile> files) {
		return ApiResponse.success(imageService.upload(files));
	}
}
