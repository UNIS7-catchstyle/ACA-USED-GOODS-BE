package com.example.app.domain.image.storage;

import com.example.app.global.exception.BusinessException;
import com.example.app.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

@Slf4j
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3ImageStorage implements ImageStorage {

	private final S3Client s3Client;
	private final String bucket;
	private final String region;
	private final String publicUrlPrefix;

	public S3ImageStorage(
			S3Client s3Client,
			@Value("${storage.s3.bucket}") String bucket,
			@Value("${storage.s3.region}") String region,
			@Value("${storage.s3.public-url-prefix:}") String publicUrlPrefix) {
		this.s3Client = s3Client;
		this.bucket = bucket;
		this.region = region;
		this.publicUrlPrefix = StringUtils.hasText(publicUrlPrefix) ? stripTrailingSlash(publicUrlPrefix) : null;
	}

	@PostConstruct
	void logActivation() {
		log.info("ImageStorage active: S3ImageStorage (bucket={}, region={})", bucket, region);
	}

	@Override
	public String upload(MultipartFile file, String key) {
		try {
			PutObjectRequest request = PutObjectRequest.builder()
					.bucket(bucket)
					.key(key)
					.contentType(file.getContentType())
					.build();
			s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
		} catch (IOException | SdkException e) {
			log.error("Failed to upload image to S3: key={}", key, e);
			throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
		}
		return buildUrl(key);
	}

	@Override
	public void delete(String url) {
		String key = extractKey(url);
		if (key == null) {
			return;
		}
		try {
			s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
		} catch (SdkException e) {
			log.warn("Failed to delete image from S3: url={}", url, e);
		}
	}

	@Override
	public boolean isOwnedUrl(String url) {
		return extractKey(url) != null;
	}

	private String buildUrl(String key) {
		if (publicUrlPrefix != null) {
			return publicUrlPrefix + "/" + key;
		}
		return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
	}

	private String extractKey(String url) {
		String prefix = buildUrl("");
		return url != null && url.startsWith(prefix) ? url.substring(prefix.length()) : null;
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
