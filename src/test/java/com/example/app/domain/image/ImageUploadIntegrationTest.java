package com.example.app.domain.image;

import com.example.app.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImageUploadIntegrationTest {

	private static final byte[] PNG_HEADER = {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
	};
	private static final byte[] JPEG_HEADER = {
			(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0
	};

	@TempDir
	static Path baseDir;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("storage.local.base-dir", baseDir::toString);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void uploadSinglePng_returns200WithOneUrlAndSavesTheFile() throws Exception {
		String token = loginAndAgreeToTerms();
		MockMultipartFile file = new MockMultipartFile("files", "photo.png", "image/png", PNG_HEADER);

		MvcResult result = mockMvc.perform(multipart("/api/images")
						.file(file)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.urls.length()").value(1))
				.andReturn();

		String url = urls(result).get(0).asText();
		String expectedPrefix = "http://localhost:8080/uploads/markets/"
				+ YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + "/";
		assertThat(url).startsWith(expectedPrefix);

		String key = url.substring("http://localhost:8080/uploads/".length());
		assertThat(Files.exists(baseDir.resolve(key))).isTrue();
	}

	@Test
	void uploadThreeFiles_preservesRequestOrder() throws Exception {
		String token = loginAndAgreeToTerms();
		MockMultipartFile first = new MockMultipartFile("files", "a.png", "image/png", PNG_HEADER);
		MockMultipartFile second = new MockMultipartFile("files", "b.jpg", "image/jpeg", JPEG_HEADER);
		MockMultipartFile third = new MockMultipartFile("files", "c.png", "image/png", PNG_HEADER);

		MvcResult result = mockMvc.perform(multipart("/api/images")
						.file(first).file(second).file(third)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode urls = urls(result);
		assertThat(urls).hasSize(3);
		assertThat(urls.get(0).asText()).endsWith(".png");
		assertThat(urls.get(1).asText()).endsWith(".jpg");
		assertThat(urls.get(2).asText()).endsWith(".png");
	}

	@Test
	void uploadTwentyOneFiles_returns400ImageLimitExceeded() throws Exception {
		String token = loginAndAgreeToTerms();
		MockMultipartHttpServletRequestBuilder request = multipart("/api/images");
		for (int i = 0; i < 21; i++) {
			request.file(new MockMultipartFile("files", "img" + i + ".png", "image/png", PNG_HEADER));
		}

		mockMvc.perform(request.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.IMAGE_LIMIT_EXCEEDED.getCode()));
	}

	@Test
	void pngExtensionButTextContent_returns400InvalidImageType() throws Exception {
		String token = loginAndAgreeToTerms();
		MockMultipartFile file = new MockMultipartFile("files", "fake.png", "image/png",
				"this is not a png".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(multipart("/api/images")
						.file(file)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_IMAGE_TYPE.getCode()));
	}

	@Test
	void zeroFiles_returns400InvalidInputValue() throws Exception {
		String token = loginAndAgreeToTerms();

		mockMvc.perform(multipart("/api/images").header("Authorization", "Bearer " + token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
	}

	@Test
	void noToken_returns401_andTermsNotAgreedToken_returns403() throws Exception {
		MockMultipartFile file = new MockMultipartFile("files", "photo.png", "image/png", PNG_HEADER);

		mockMvc.perform(multipart("/api/images").file(file))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

		String notAgreedToken = login();
		mockMvc.perform(multipart("/api/images")
						.file(new MockMultipartFile("files", "photo.png", "image/png", PNG_HEADER))
						.header("Authorization", "Bearer " + notAgreedToken))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ErrorCode.TERMS_NOT_AGREED.getCode()));
	}

	@Test
	void staticServing_returns200WithImagePngContentType() throws Exception {
		String token = loginAndAgreeToTerms();
		MockMultipartFile file = new MockMultipartFile("files", "photo.png", "image/png", PNG_HEADER);

		MvcResult uploadResult = mockMvc.perform(multipart("/api/images")
						.file(file)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();

		String url = urls(uploadResult).get(0).asText();
		String path = url.substring("http://localhost:8080".length());

		mockMvc.perform(get(path))
				.andExpect(status().isOk())
				.andExpect(result -> assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.IMAGE_PNG_VALUE));
	}

	private JsonNode urls(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("urls");
	}

	private String loginAndAgreeToTerms() throws Exception {
		String accessToken = login();
		mockMvc.perform(post("/api/users/me/terms")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"requiredAgreed\":true,\"marketingEmailAgreed\":false,\"marketingSnsAgreed\":false}"))
				.andExpect(status().isOk());
		return accessToken;
	}

	private String login() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accessToken\":\"any\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.get("data").get("accessToken").asText();
	}
}
