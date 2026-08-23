package com.example.app.domain.market;

import com.example.app.domain.auth.entity.Provider;
import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import com.example.app.domain.market.entity.MarketImage;
import com.example.app.domain.market.repository.MarketImageRepository;
import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.domain.scrap.entity.Scrap;
import com.example.app.domain.scrap.repository.ScrapRepository;
import com.example.app.domain.setting.entity.AppSetting;
import com.example.app.domain.setting.repository.AppSettingRepository;
import com.example.app.domain.setting.service.AppSettingService;
import com.example.app.domain.user.entity.User;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.global.exception.ErrorCode;
import com.example.app.global.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MarketFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MarketRepository marketRepository;

	@Autowired
	private MarketImageRepository marketImageRepository;

	@Autowired
	private ScrapRepository scrapRepository;

	@Autowired
	private AppSettingRepository appSettingRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	// ---- list (1-5) ----

	@Test
	void list_pagination_returnsCorrectPagesAndTotalCountWithNoOverlap() throws Exception {
		Set<Long> seededIds = new HashSet<>();
		for (int i = 0; i < 30; i++) {
			seededIds.add(seedMarket(Category.KPOP, false).getId());
		}

		MvcResult firstPage = mockMvc.perform(get("/api/markets").param("category", "KPOP").param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(20))
				.andExpect(jsonPath("$.data.hasNext").value(true))
				.andExpect(jsonPath("$.data.totalCount").value(30))
				.andReturn();
		JsonNode firstData = dataOf(firstPage);
		String nextCursor = firstData.get("nextCursor").asText();
		assertThat(nextCursor).isNotBlank();
		Set<Long> firstIds = idsOf(firstData);

		MvcResult secondPage = mockMvc
				.perform(get("/api/markets").param("category", "KPOP").param("size", "20").param("cursor", nextCursor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(10))
				.andExpect(jsonPath("$.data.hasNext").value(false))
				.andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.nullValue()))
				.andReturn();
		Set<Long> secondIds = idsOf(dataOf(secondPage));

		assertThat(firstIds).hasSize(20);
		assertThat(secondIds).hasSize(10);
		assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
		Set<Long> combined = new HashSet<>(firstIds);
		combined.addAll(secondIds);
		assertThat(combined).isEqualTo(seededIds);
	}

	@Test
	void list_excludeClosed_filtersMarketsAndTotalCount() throws Exception {
		seedMarket(Category.TWO_D, false);
		seedMarket(Category.TWO_D, false);
		Market closed = seedMarket(Category.TWO_D, true);

		MvcResult result = mockMvc
				.perform(get("/api/markets").param("category", "TWO_D").param("excludeClosed", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(2))
				.andExpect(jsonPath("$.data.items.length()").value(2))
				.andReturn();

		assertThat(idsOf(dataOf(result))).doesNotContain(closed.getId());
	}

	@Test
	void list_missingCategory_returns400() throws Exception {
		mockMvc.perform(get("/api/markets"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
	}

	@Test
	void list_sizeOutOfRange_returns400() throws Exception {
		mockMvc.perform(get("/api/markets").param("category", "KPOP").param("size", "51"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
	}

	@Test
	void list_brokenCursor_returns400InvalidCursor() throws Exception {
		mockMvc.perform(get("/api/markets").param("category", "KPOP").param("cursor", "!!not-base64!!"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CURSOR.getCode()));
	}

	@Test
	void list_scrapStatus_falseWhenAnonymousTrueOnlyForScrappedMarket() throws Exception {
		Market scrappedMarket = seedMarket(Category.MUSICAL, false);
		Market otherMarket = seedMarket(Category.MUSICAL, false);

		MvcResult anonymousResult = mockMvc.perform(get("/api/markets").param("category", "MUSICAL"))
				.andExpect(status().isOk())
				.andReturn();
		for (JsonNode item : dataOf(anonymousResult).get("items")) {
			assertThat(item.get("isScrapped").asBoolean()).isFalse();
		}

		User loggedInUser = createAgreedUser("scrap-viewer");
		scrapRepository.save(Scrap.builder().user(loggedInUser).market(scrappedMarket).build());
		String token = tokenFor(loggedInUser);

		MvcResult loggedInResult = mockMvc
				.perform(get("/api/markets").param("category", "MUSICAL").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode items = dataOf(loggedInResult).get("items");
		assertThat(findById(items, scrappedMarket.getId()).get("isScrapped").asBoolean()).isTrue();
		assertThat(findById(items, otherMarket.getId()).get("isScrapped").asBoolean()).isFalse();
	}

	@Test
	void list_thumbnails_capAtThreeInSortOrderAndEmptyWhenNoImages() throws Exception {
		Market withImages = seedMarket(Category.ETC, false);
		saveImage(withImages, "url-2", 2);
		saveImage(withImages, "url-0", 0);
		saveImage(withImages, "url-1", 1);
		saveImage(withImages, "url-3", 3);
		Market withoutImages = seedMarket(Category.ETC, false);

		MvcResult result = mockMvc.perform(get("/api/markets").param("category", "ETC"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode items = dataOf(result).get("items");

		List<String> thumbnails = toStringList(findById(items, withImages.getId()).get("thumbnails"));
		assertThat(thumbnails).containsExactly("url-0", "url-1", "url-2");
		assertThat(toStringList(findById(items, withoutImages.getId()).get("thumbnails"))).isEmpty();
	}

	// ---- register (6-9) ----

	@Test
	void register_success_createsMarketAndOrderedMarketImages() throws Exception {
		String token = loginAndAgreeToTerms();
		String body = """
				{"category":"KPOP","title":"my market","itemCategories":"nct wish, f1","description":"desc",
				 "imageUrls":["http://localhost:8080/uploads/a.png","http://localhost:8080/uploads/b.png","http://localhost:8080/uploads/c.png"]}
				""";

		MvcResult result = mockMvc.perform(post("/api/markets")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn();

		Long marketId = dataOf(result).get("id").asLong();
		Market saved = marketRepository.findById(marketId).orElseThrow();
		assertThat(saved.getTitle()).isEqualTo("my market");
		assertThat(saved.isClosed()).isFalse();

		List<MarketImage> images = marketImageRepository.findByMarketIdInOrderBySortOrderAsc(List.of(marketId));
		assertThat(images).hasSize(3);
		assertThat(images.get(0).getSortOrder()).isZero();
		assertThat(images.get(0).getImageUrl()).isEqualTo("http://localhost:8080/uploads/a.png");
		assertThat(images.get(1).getSortOrder()).isEqualTo(1);
		assertThat(images.get(2).getSortOrder()).isEqualTo(2);
	}

	@Test
	void register_secondTimeBySameUser_returns409MarketAlreadyExists() throws Exception {
		String token = loginAndAgreeToTerms();
		String body = "{\"category\":\"KPOP\",\"title\":\"t\",\"itemCategories\":\"c\",\"description\":\"d\"}";

		mockMvc.perform(post("/api/markets").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/markets").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(ErrorCode.MARKET_ALREADY_EXISTS.getCode()));
	}

	@Test
	void register_whenRegistrationClosed_returns403AndStatusEndpointReflectsClosed() throws Exception {
		closeMarketRegistration();

		mockMvc.perform(get("/api/markets/registration-status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.open").value(false));

		String token = loginAndAgreeToTerms();
		String body = "{\"category\":\"KPOP\",\"title\":\"t\",\"itemCategories\":\"c\",\"description\":\"d\"}";
		mockMvc.perform(post("/api/markets").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ErrorCode.MARKET_REGISTRATION_CLOSED.getCode()));
	}

	@Test
	void register_moreThanTwentyImageUrls_returns400() throws Exception {
		String token = loginAndAgreeToTerms();
		List<String> urls = new ArrayList<>();
		for (int i = 0; i < 21; i++) {
			urls.add("http://localhost:8080/uploads/img" + i + ".png");
		}
		String body = objectMapper.writeValueAsString(new MarketBody("KPOP", "t", "c", "d", urls, null));

		mockMvc.perform(post("/api/markets").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
	}

	@Test
	void register_externalImageUrl_returns400InvalidImageUrl() throws Exception {
		String token = loginAndAgreeToTerms();
		String body = "{\"category\":\"KPOP\",\"title\":\"t\",\"itemCategories\":\"c\",\"description\":\"d\","
				+ "\"imageUrls\":[\"https://evil.example.com/a.png\"]}";

		mockMvc.perform(post("/api/markets").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_IMAGE_URL.getCode()));
	}

	@Test
	void register_titleOver100Chars_returns400() throws Exception {
		String token = loginAndAgreeToTerms();
		String body = objectMapper.writeValueAsString(new MarketBody("KPOP", "a".repeat(101), "c", "d", null, null));

		mockMvc.perform(post("/api/markets").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
	}

	// ---- detail / update (10-15) ----

	@Test
	void detail_anonymous_ownerFalseScrappedFalseCommentsEmpty() throws Exception {
		User owner = createAgreedUser("owner10");
		Market market = marketRepository.save(baseMarket(owner, Category.ETC));

		mockMvc.perform(get("/api/markets/" + market.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isOwner").value(false))
				.andExpect(jsonPath("$.data.isScrapped").value(false))
				.andExpect(jsonPath("$.data.comments").isArray())
				.andExpect(jsonPath("$.data.comments.length()").value(0));
	}

	@Test
	void detail_owner_returnsOwnerTrue() throws Exception {
		User owner = createAgreedUser("owner11");
		Market market = marketRepository.save(baseMarket(owner, Category.ETC));
		String token = tokenFor(owner);

		mockMvc.perform(get("/api/markets/" + market.getId()).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isOwner").value(true));
	}

	@Test
	void update_byNonOwner_returns403Forbidden() throws Exception {
		User owner = createAgreedUser("owner12");
		User stranger = createAgreedUser("stranger12");
		Market market = marketRepository.save(baseMarket(owner, Category.ETC));
		String strangerToken = tokenFor(stranger);
		String body = "{\"category\":\"ETC\",\"title\":\"new\",\"itemCategories\":\"c\",\"description\":\"d\"}";

		mockMvc.perform(put("/api/markets/" + market.getId()).header("Authorization", "Bearer " + strangerToken)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
	}

	@Test
	void update_byOwner_replacesFieldsAndImages() throws Exception {
		User owner = createAgreedUser("owner13");
		Market market = marketRepository.save(baseMarket(owner, Category.ETC));
		saveImage(market, "http://localhost:8080/uploads/old1.png", 0);
		saveImage(market, "http://localhost:8080/uploads/old2.png", 1);
		String token = tokenFor(owner);
		String body = "{\"category\":\"KPOP\",\"title\":\"new title\",\"itemCategories\":\"nc\",\"description\":\"nd\","
				+ "\"isClosed\":true,\"imageUrls\":[\"http://localhost:8080/uploads/old1.png\"]}";

		mockMvc.perform(put("/api/markets/" + market.getId()).header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.isClosed").value(true))
				.andExpect(jsonPath("$.data.title").value("new title"))
				.andExpect(jsonPath("$.data.images.length()").value(1));

		List<MarketImage> images = marketImageRepository.findByMarketIdInOrderBySortOrderAsc(List.of(market.getId()));
		assertThat(images).hasSize(1);
		assertThat(images.get(0).getImageUrl()).isEqualTo("http://localhost:8080/uploads/old1.png");
	}

	@Test
	void update_whenRegistrationClosed_stillSucceeds() throws Exception {
		closeMarketRegistration();
		User owner = createAgreedUser("owner14");
		Market market = marketRepository.save(baseMarket(owner, Category.ETC));
		String token = tokenFor(owner);
		String body = "{\"category\":\"ETC\",\"title\":\"updated\",\"itemCategories\":\"c\",\"description\":\"d\"}";

		mockMvc.perform(put("/api/markets/" + market.getId()).header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("updated"));
	}

	@Test
	void detail_nonExistentId_returns404MarketNotFound() throws Exception {
		mockMvc.perform(get("/api/markets/999999999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCode.MARKET_NOT_FOUND.getCode()));
	}

	@Test
	void update_nonExistentId_returns404MarketNotFound() throws Exception {
		User owner = createAgreedUser("owner15");
		String token = tokenFor(owner);
		String body = "{\"category\":\"ETC\",\"title\":\"t\",\"itemCategories\":\"c\",\"description\":\"d\"}";

		mockMvc.perform(put("/api/markets/999999999").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCode.MARKET_NOT_FOUND.getCode()));
	}

	// ---- security (17) ----

	@Test
	void unimplementedScrapEndpointWithoutToken_returns401NotFound() throws Exception {
		// Security's anyRequest().authenticated() must reject this before dispatch
		// ever looks for a (currently nonexistent) handler — 401, never 404.
		mockMvc.perform(post("/api/markets/1/scrap"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));
	}

	// ---- helpers ----

	private Market seedMarket(Category category, boolean closed) {
		User owner = createAgreedUser("seed");
		Market market = marketRepository.save(baseMarket(owner, category));
		if (closed) {
			market.update(category, market.getTitle(), market.getItemCategories(), market.getDescription(), true);
		}
		return market;
	}

	private Market baseMarket(User owner, Category category) {
		return Market.builder()
				.user(owner)
				.category(category)
				.title("title")
				.itemCategories("cats")
				.description("desc")
				.build();
	}

	private void saveImage(Market market, String url, int sortOrder) {
		marketImageRepository.save(MarketImage.builder().market(market).imageUrl(url).sortOrder(sortOrder).build());
	}

	private User createAgreedUser(String label) {
		return userRepository.save(User.builder()
				.provider(Provider.GOOGLE)
				.providerId(label + "-" + UUID.randomUUID())
				.nickname(label + "-" + UUID.randomUUID())
				.termsAgreedAt(LocalDateTime.now())
				.build());
	}

	private String tokenFor(User user) {
		return jwtTokenProvider.generateAccessToken(user.getId());
	}

	private void closeMarketRegistration() {
		appSettingRepository.save(AppSetting.builder()
				.settingKey(AppSettingService.MARKET_REGISTRATION_OPEN_KEY)
				.settingValue("false")
				.build());
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

	private Set<Long> idsOf(JsonNode data) {
		Set<Long> ids = new HashSet<>();
		data.get("items").forEach(item -> ids.add(item.get("id").asLong()));
		return ids;
	}

	private JsonNode findById(JsonNode items, Long id) {
		return StreamSupport.stream(items.spliterator(), false)
				.filter(item -> item.get("id").asLong() == id)
				.findFirst()
				.orElseThrow(() -> new AssertionError("market " + id + " not found in response"));
	}

	private List<String> toStringList(JsonNode arrayNode) {
		List<String> values = new ArrayList<>();
		arrayNode.forEach(node -> values.add(node.asText()));
		return values;
	}

	private record MarketBody(String category, String title, String itemCategories, String description,
							   List<String> imageUrls, Boolean isClosed) {
	}
}
