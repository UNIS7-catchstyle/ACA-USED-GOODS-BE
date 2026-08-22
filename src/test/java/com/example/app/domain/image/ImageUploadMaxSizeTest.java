package com.example.app.domain.image;

import com.example.app.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real embedded servlet container (not MockMvc, which pre-parses
 * multipart parts and never applies spring.servlet.multipart.max-file-size), so
 * MaxUploadSizeExceededException actually fires before the controller runs.
 *
 * <p>Depending on OS/JDK socket behavior, Tomcat may detect the per-file size
 * violation mid-stream and abort the connection before it can send a clean HTTP
 * response, racing against the client still writing the body (an HTTP/1.1
 * limitation without "Expect: 100-continue", not a bug in this app — see
 * GlobalExceptionHandlerTest for a deterministic unit test of the actual
 * exception-to-error-code mapping). This test accepts either outcome, asserting
 * the precise error code whenever a clean response is available.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ImageUploadMaxSizeTest {

	@TempDir
	static Path baseDir;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("storage.local.base-dir", baseDir::toString);
	}

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void fileOverTenMb_triggersMaxUploadSizeExceeded_mappedToImageSizeExceeded() throws Exception {
		String accessToken = loginAndAgreeToTerms();

		byte[] oversized = new byte[10 * 1024 * 1024 + 1];
		oversized[0] = (byte) 0x89;
		oversized[1] = 0x50;
		oversized[2] = 0x4E;
		oversized[3] = 0x47;

		ByteArrayResource resource = new ByteArrayResource(oversized) {
			@Override
			public String getFilename() {
				return "big.png";
			}
		};
		HttpHeaders partHeaders = new HttpHeaders();
		partHeaders.setContentType(MediaType.IMAGE_PNG);

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("files", new HttpEntity<>(resource, partHeaders));

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.setBearerAuth(accessToken);

		try {
			ResponseEntity<String> response = restTemplate.postForEntity(
					"/api/images", new HttpEntity<>(body, headers), String.class);

			assertThat(response.getStatusCode().value()).isEqualTo(400);
			JsonNode json = objectMapper.readTree(response.getBody());
			assertThat(json.get("code").asText()).isEqualTo(ErrorCode.IMAGE_SIZE_EXCEEDED.getCode());
		} catch (ResourceAccessException e) {
			assertThat(e.getCause()).isInstanceOf(SocketException.class);
		}
	}

	private String loginAndAgreeToTerms() throws Exception {
		ResponseEntity<String> loginResponse = restTemplate.postForEntity(
				"/api/auth/login/kakao", jsonEntity("{\"accessToken\":\"any\"}"), String.class);
		String accessToken = objectMapper.readTree(loginResponse.getBody())
				.get("data").get("accessToken").asText();

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(accessToken);
		HttpEntity<String> termsRequest = new HttpEntity<>(
				"{\"requiredAgreed\":true,\"marketingEmailAgreed\":false,\"marketingSnsAgreed\":false}", headers);
		restTemplate.postForEntity("/api/users/me/terms", termsRequest, String.class);

		return accessToken;
	}

	private HttpEntity<String> jsonEntity(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(body, headers);
	}
}
