package com.example.app.domain.image;

import com.example.app.domain.image.storage.S3ImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageTest {

	@Mock
	private S3Client s3Client;

	@Test
	void upload_putsToBucketWithContentTypeAndReturnsDirectS3Url() {
		S3ImageStorage storage = new S3ImageStorage(s3Client, "test-bucket", "ap-northeast-2", null);
		MockMultipartFile file = new MockMultipartFile("files", "photo.png", "image/png", new byte[]{1, 2, 3});

		String url = storage.upload(file, "markets/202608/abc.png");

		ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
		PutObjectRequest request = requestCaptor.getValue();
		assertThat(request.bucket()).isEqualTo("test-bucket");
		assertThat(request.key()).isEqualTo("markets/202608/abc.png");
		assertThat(request.contentType()).isEqualTo("image/png");
		assertThat(url).isEqualTo("https://test-bucket.s3.ap-northeast-2.amazonaws.com/markets/202608/abc.png");
	}

	@Test
	void upload_withPublicUrlPrefix_returnsPrefixedUrl() {
		S3ImageStorage storage = new S3ImageStorage(s3Client, "test-bucket", "ap-northeast-2", "https://cdn.example.com");
		MockMultipartFile file = new MockMultipartFile("files", "photo.png", "image/png", new byte[]{1, 2, 3});

		String url = storage.upload(file, "markets/202608/abc.png");

		assertThat(url).isEqualTo("https://cdn.example.com/markets/202608/abc.png");
	}

	@Test
	void delete_deletesByKeyExtractedFromUrl() {
		S3ImageStorage storage = new S3ImageStorage(s3Client, "test-bucket", "ap-northeast-2", null);

		storage.delete("https://test-bucket.s3.ap-northeast-2.amazonaws.com/markets/202608/abc.png");

		ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
		verify(s3Client).deleteObject(requestCaptor.capture());
		assertThat(requestCaptor.getValue().bucket()).isEqualTo("test-bucket");
		assertThat(requestCaptor.getValue().key()).isEqualTo("markets/202608/abc.png");
	}
}
