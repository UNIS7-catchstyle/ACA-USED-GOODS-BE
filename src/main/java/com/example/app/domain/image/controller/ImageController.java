package com.example.app.domain.image.controller;

import com.example.app.domain.image.dto.ImageUploadResponse;
import com.example.app.domain.image.service.ImageService;
import com.example.app.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {

	private final ImageService imageService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<ImageUploadResponse> upload(
			@RequestPart(value = "files", required = false) List<MultipartFile> files) {
		return ApiResponse.success(imageService.upload(files));
	}
}
