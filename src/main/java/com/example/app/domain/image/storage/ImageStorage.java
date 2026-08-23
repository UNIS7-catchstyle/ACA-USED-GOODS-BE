package com.example.app.domain.image.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {

	String upload(MultipartFile file, String key);

	void delete(String url);

	// Whether this URL was issued by the currently active storage implementation
	// (i.e. its prefix matches this storage's local base-url or S3 domain).
	boolean isOwnedUrl(String url);
}
