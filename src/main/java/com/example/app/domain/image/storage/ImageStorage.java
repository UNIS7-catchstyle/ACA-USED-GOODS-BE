package com.example.app.domain.image.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {

	String upload(MultipartFile file, String key);

	void delete(String url);
}
