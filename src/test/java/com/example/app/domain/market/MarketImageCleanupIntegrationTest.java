package com.example.app.domain.market;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Deliberately NOT @Transactional: MarketService registers the ImageStorage.delete
// call via TransactionSynchronizationManager's afterCommit, which never fires inside
// a test-managed transaction that only ever rolls back. This class runs its own real
// commits instead, isolated into its own H2 instance/context via @DynamicPropertySource
// (same trick ImageUploadIntegrationTest uses), so it can't pollute other test classes.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketImageCleanupIntegrationTest {

	private static final byte[] PNG_HEADER = {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
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
	void update_removingAnImage_deletesItsFileFromDiskAfterCommit() throws Exception {
		String token = loginAndAgreeToTerms();

		String keptUrl = uploadImage(token, "a.png");
		String removedUrl = uploadImage(token, "b.png");
		String keptKey = keyOf(keptUrl);
		String removedKey = keyOf(removedUrl);
		assertThat(Files.exists(baseDir.resolve(keptKey))).isTrue();
		assertThat(Files.exists(baseDir.resolve(removedKey))).isTrue();

		String registerBody = objectMapper.writeValueAsString(
				new MarketBody("KPOP", "t", "c", "d", List.of(keptUrl, removedUrl), null));
		MvcResult registerResult = mockMvc.perform(post("/api/markets")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(registerBody))
				.andExpect(status().isCreated())
				.andReturn();
		Long marketId = dataOf(registerResult).get("id").asLong();

		String updateBody = objectMapper.writeValueAsString(new MarketBody("KPOP", "t", "c", "d", List.of(keptUrl), null));
		mockMvc.perform(put("/api/markets/" + marketId)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(updateBody))
				.andExpect(status().isOk());

		// The PUT's own @Transactional service method genuinely committed (no
		// surrounding test transaction here), so afterCommit already ran by the
		// time this assertion runs — no polling/Awaitility needed.
		assertThat(Files.exists(baseDir.resolve(keptKey))).isTrue();
		assertThat(Files.exists(baseDir.resolve(removedKey))).isFalse();
	}

	@Test
	void withdraw_withTwoMarkets_deletesAllImageFilesFromDisk() throws Exception {
		String token = loginAndAgreeToTerms();

		String market1Image = uploadImage(token, "m1.png");
		String market2Image = uploadImage(token, "m2.png");
		String market1Key = keyOf(market1Image);
		String market2Key = keyOf(market2Image);

		String market1Body = objectMapper.writeValueAsString(
				new MarketBody("KPOP", "market1", "c", "d", List.of(market1Image), null));
		String market2Body = objectMapper.writeValueAsString(
				new MarketBody("ETC", "market2", "c", "d", List.of(market2Image), null));
		mockMvc.perform(post("/api/markets")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(market1Body))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/markets")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(market2Body))
				.andExpect(status().isCreated());
		assertThat(Files.exists(baseDir.resolve(market1Key))).isTrue();
		assertThat(Files.exists(baseDir.resolve(market2Key))).isTrue();

		mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		// No surrounding test transaction here, so DELETE /api/users/me's own
		// @Transactional service method genuinely committed and afterCommit already
		// ran by the time this assertion runs.
		assertThat(Files.exists(baseDir.resolve(market1Key))).isFalse();
		assertThat(Files.exists(baseDir.resolve(market2Key))).isFalse();
	}

	private String uploadImage(String token, String filename) throws Exception {
		MockMultipartFile file = new MockMultipartFile("files", filename, "image/png", PNG_HEADER);
		MvcResult result = mockMvc.perform(multipart("/api/images")
						.file(file)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();
		return dataOf(result).get("urls").get(0).asText();
	}

	private String keyOf(String url) {
		String prefix = "http://localhost:8080/uploads/";
		return url.substring(prefix.length());
	}

	private String loginAndAgreeToTerms() throws Exception {
		MvcResult loginResult = mockMvc.perform(post("/api/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accessToken\":\"any\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String accessToken = dataOf(loginResult).get("accessToken").asText();

		mockMvc.perform(post("/api/users/me/terms")
						.header("Authorization", "Bearer " + accessToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"requiredAgreed\":true,\"marketingEmailAgreed\":false,\"marketingSnsAgreed\":false}"))
				.andExpect(status().isOk());
		return accessToken;
	}

	private JsonNode dataOf(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private record MarketBody(String category, String title, String itemCategories, String description,
							   List<String> imageUrls, Boolean isClosed) {
	}
}
