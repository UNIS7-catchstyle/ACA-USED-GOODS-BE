package com.example.app.domain.image.service;

import com.example.app.domain.image.dto.ImageUploadResponse;
import com.example.app.domain.image.storage.ImageStorage;
import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {

	private static final int MAX_FILE_COUNT = 20;
	private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
	private static final int HEADER_BYTES = 16;
	private static final DateTimeFormatter KEY_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

	private final ImageStorage imageStorage;

	public ImageUploadResponse upload(List<MultipartFile> files) {
		if (files == null || files.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}
		if (files.size() > MAX_FILE_COUNT) {
			throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
		}

		List<String> urls = new ArrayList<>(files.size());
		for (int i = 0; i < files.size(); i++) {
			urls.add(uploadOne(files.get(i), i));
		}
		return new ImageUploadResponse(urls);
	}

	private String uploadOne(MultipartFile file, int index) {
		ImageType type = validate(file, index);
		String key = buildKey(type);
		return imageStorage.upload(file, key);
	}

	private ImageType validate(MultipartFile file, int index) {
		if (file.getSize() > MAX_FILE_SIZE_BYTES) {
			throw new BusinessException(ErrorCode.IMAGE_SIZE_EXCEEDED,
					(index + 1) + "번째 파일이 10MB를 초과했습니다.");
		}
		// Checked ahead of the magic-number read below: an empty file has no bytes
		// to sniff, and would otherwise surface as the less specific INVALID_IMAGE_TYPE.
		if (file.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
		}

		String declaredExtension = extractExtension(file.getOriginalFilename());
		ImageType declaredType = ImageType.fromExtension(declaredExtension)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_IMAGE_TYPE));

		ImageType detectedType = ImageType.detect(readHeader(file, index))
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_IMAGE_TYPE));

		if (declaredType != detectedType) {
			throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE);
		}
		return detectedType;
	}

	private String extractExtension(String filename) {
		if (filename == null) {
			return null;
		}
		int dotIndex = filename.lastIndexOf('.');
		return dotIndex < 0 || dotIndex == filename.length() - 1 ? null : filename.substring(dotIndex + 1);
	}

	private byte[] readHeader(MultipartFile file, int index) {
		try (InputStream inputStream = file.getInputStream()) {
			byte[] header = new byte[HEADER_BYTES];
			int read = inputStream.read(header);
			return read <= 0 ? new byte[0] : Arrays.copyOf(header, read);
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.INVALID_IMAGE_TYPE, (index + 1) + "번째 파일을 읽을 수 없습니다.");
		}
	}

	private String buildKey(ImageType type) {
		String yearMonth = YearMonth.now().format(KEY_MONTH_FORMAT);
		return "markets/" + yearMonth + "/" + UUID.randomUUID() + "." + type.extension();
	}
}
