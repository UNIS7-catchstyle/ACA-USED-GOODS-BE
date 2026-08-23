package com.example.app.domain.image.storage;

import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {

	private final Path baseDir;
	private final String baseUrl;

	public LocalImageStorage(
			@Value("${storage.local.base-dir:./uploads}") String baseDir,
			@Value("${storage.local.base-url:http://localhost:8080/uploads}") String baseUrl) {
		this.baseDir = Paths.get(baseDir);
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	@PostConstruct
	void logActivation() {
		log.info("ImageStorage active: LocalImageStorage (baseDir={})", baseDir.toAbsolutePath());
	}

	@Override
	public String upload(MultipartFile file, String key) {
		Path target = baseDir.resolve(key);
		try {
			Files.createDirectories(target.getParent());
			file.transferTo(target);
		} catch (IOException e) {
			log.error("Failed to save image locally: key={}", key, e);
			throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
		}
		return baseUrl + "/" + key;
	}

	@Override
	public void delete(String url) {
		String key = extractKey(url);
		if (key == null) {
			return;
		}
		try {
			Files.deleteIfExists(baseDir.resolve(key));
		} catch (IOException e) {
			log.warn("Failed to delete local image: url={}", url, e);
		}
	}

	@Override
	public boolean isOwnedUrl(String url) {
		return extractKey(url) != null;
	}

	private String extractKey(String url) {
		String prefix = baseUrl + "/";
		return url != null && url.startsWith(prefix) ? url.substring(prefix.length()) : null;
	}
}
