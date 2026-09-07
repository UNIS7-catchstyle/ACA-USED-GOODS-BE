package com.example.app.domain.scrap;

import com.example.app.domain.auth.entity.Provider;
import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.domain.scrap.repository.ScrapRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScrapFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MarketRepository marketRepository;

	@Autowired
	private ScrapRepository scrapRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void scrap_success_incrementsCountAndCreatesRow() throws Exception {
		User owner = createAgreedUser("owner1");
		User scraper = createAgreedUser("scraper1");
		Market market = seedMarket(owner, Category.KPOP, false);

		mockMvc.perform(post("/api/markets/" + market.getId() + "/scrap").header("Authorization", "Bearer " + tokenFor(scraper)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.scrapCount").value(1))
				.andExpect(jsonPath("$.data.isScrapped").value(true));

		assertThat(scrapRepository.existsByUserIdAndMarketId(scraper.getId(), market.getId())).isTrue();
		assertThat(marketRepository.findById(market.getId()).orElseThrow().getScrapCount()).isEqualTo(1);
	}

	@Test
	void scrap_alreadyScrapped_returns409AndCountUnchanged() throws Exception {
		User owner = createAgreedUser("owner2");
		User scraper = createAgreedUser("scraper2");
		Market market = seedMarket(owner, Category.KPOP, false);
		String token = tokenFor(scraper);

		mockMvc.perform(post("/api/markets/" + market.getId() + "/scrap").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/markets/" + market.getId() + "/scrap").header("Authorization", "Bearer " + token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value(ErrorCode.SCRAP_ALREADY_EXISTS.getCode()));

		assertThat(marketRepository.findById(market.getId()).orElseThrow().getScrapCount()).isEqualTo(1);
	}

	@Test
	void scrap_ownMarket_returns400SelfScrapNotAllowed() throws Exception {
		User owner = createAgreedUser("owner2b");
		Market market = seedMarket(owner, Category.KPOP, false);

		mockMvc.perform(post("/api/markets/" + market.getId() + "/scrap").header("Authorization", "Bearer " + tokenFor(owner)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.SELF_SCRAP_NOT_ALLOWED.getCode()));

		assertThat(scrapRepository.count()).isZero();
		assertThat(marketRepository.findById(market.getId()).orElseThrow().getScrapCount()).isZero();
	}

	@Test
	void unscrap_success_decrementsCountAndRemovesRow() throws Exception {
		User owner = createAgreedUser("owner3");
		User scraper = createAgreedUser("scraper3");
		Market market = seedMarket(owner, Category.KPOP, false);
		String token = tokenFor(scraper);
		mockMvc.perform(post("/api/markets/" + market.getId() + "/scrap").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/markets/" + market.getId() + "/scrap").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.scrapCount").value(0))
				.andExpect(jsonPath("$.data.isScrapped").value(false));

		assertThat(scrapRepository.count()).isZero();
		assertThat(marketRepository.findById(market.getId()).orElseThrow().getScrapCount()).isZero();
	}

	@Test
	void unscrap_notScrapped_isIdempotentNoChange() throws Exception {
		User owner = createAgreedUser("owner4");
		User stranger = createAgreedUser("stranger4");
		Market market = seedMarket(owner, Category.KPOP, false);

		mockMvc.perform(delete("/api/markets/" + market.getId() + "/scrap").header("Authorization", "Bearer " + tokenFor(stranger)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.scrapCount").value(0))
				.andExpect(jsonPath("$.data.isScrapped").value(false));
	}

	@Test
	void unscrap_onMarketWithNoScraps_staysZeroNeverGoesNegative() throws Exception {
		User owner = createAgreedUser("owner5");
		User stranger = createAgreedUser("stranger5");
		Market market = seedMarket(owner, Category.KPOP, false);
		assertThat(market.getScrapCount()).isZero();

		mockMvc.perform(delete("/api/markets/" + market.getId() + "/scrap").header("Authorization", "Bearer " + tokenFor(stranger)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.scrapCount").value(0));

		assertThat(marketRepository.findById(market.getId()).orElseThrow().getScrapCount()).isZero();
	}

	@Test
	void scrap_nonExistentMarket_returns404() throws Exception {
		User user = createAgreedUser("user6");

		mockMvc.perform(post("/api/markets/999999999/scrap").header("Authorization", "Bearer " + tokenFor(user)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCode.MARKET_NOT_FOUND.getCode()));
	}

	@Test
	void unscrap_nonExistentMarket_returns404() throws Exception {
		User user = createAgreedUser("user7");

		mockMvc.perform(delete("/api/markets/999999999/scrap").header("Authorization", "Bearer " + tokenFor(user)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value(ErrorCode.MARKET_NOT_FOUND.getCode()));
	}

	@Test
	void scrap_thenListMarkets_reflectsCountAndIsScrappedPerViewer() throws Exception {
		User owner = createAgreedUser("owner8");
		User scraper = createAgreedUser("scraper8");
		User otherViewer = createAgreedUser("viewer8");
		Market market = seedMarket(owner, Category.MUSICAL, false);

		mockMvc.perform(post("/api/markets/" + market.getId() + "/scrap").header("Authorization", "Bearer " + tokenFor(scraper)))
				.andExpect(status().isOk());

		MvcResult scraperView = mockMvc
				.perform(get("/api/markets").param("category", "MUSICAL").header("Authorization", "Bearer " + tokenFor(scraper)))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode scraperItem = findById(dataOf(scraperView).get("items"), market.getId());
		assertThat(scraperItem.get("scrapCount").asInt()).isEqualTo(1);
		assertThat(scraperItem.get("isScrapped").asBoolean()).isTrue();

		MvcResult otherView = mockMvc
				.perform(get("/api/markets").param("category", "MUSICAL").header("Authorization", "Bearer " + tokenFor(otherViewer)))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode otherItem = findById(dataOf(otherView).get("items"), market.getId());
		assertThat(otherItem.get("scrapCount").asInt()).isEqualTo(1);
		assertThat(otherItem.get("isScrapped").asBoolean()).isFalse();
	}

	@Test
	void myScraps_orderedByScrapTimeAllScrappedTrueTotalCountMatches() throws Exception {
		User ownerA = createAgreedUser("ownerA9");
		User ownerB = createAgreedUser("ownerB9");
		User scraper = createAgreedUser("scraper9");
		Market marketA = seedMarket(ownerA, Category.ETC, false);
		Market marketB = seedMarket(ownerB, Category.ETC, false);
		String token = tokenFor(scraper);

		// Scrap A first, then B — B was scrapped more recently and must come first.
		mockMvc.perform(post("/api/markets/" + marketA.getId() + "/scrap").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/markets/" + marketB.getId() + "/scrap").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(get("/api/users/me/scraps").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(2))
				.andReturn();
		JsonNode items = dataOf(result).get("items");
		assertThat(items.get(0).get("id").asLong()).isEqualTo(marketB.getId());
		assertThat(items.get(1).get("id").asLong()).isEqualTo(marketA.getId());
		for (JsonNode item : items) {
			assertThat(item.get("isScrapped").asBoolean()).isTrue();
		}
	}

	@Test
	void myScraps_categoryFilter_returnsOnlyThatCategory() throws Exception {
		User ownerA = createAgreedUser("ownA10");
		User ownerB = createAgreedUser("ownB10");
		User scraper = createAgreedUser("scraper10");
		Market kpopMarket = seedMarket(ownerA, Category.KPOP, false);
		Market etcMarket = seedMarket(ownerB, Category.ETC, false);
		String token = tokenFor(scraper);
		mockMvc.perform(post("/api/markets/" + kpopMarket.getId() + "/scrap").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/markets/" + etcMarket.getId() + "/scrap").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		MvcResult result = mockMvc
				.perform(get("/api/users/me/scraps").param("category", "KPOP").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(1))
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andReturn();
		assertThat(dataOf(result).get("items").get(0).get("id").asLong()).isEqualTo(kpopMarket.getId());
	}

	@Test
	void myScraps_excludeClosed_excludesClosedMarkets() throws Exception {
		User ownerA = createAgreedUser("ownA11");
		User ownerB = createAgreedUser("ownB11");
		User scraper = createAgreedUser("scraper11");
		Market openMarket = seedMarket(ownerA, Category.ETC, false);
		Market closedMarket = seedMarket(ownerB, Category.ETC, true);
		String token = tokenFor(scraper);
		mockMvc.perform(post("/api/markets/" + openMarket.getId() + "/scrap").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/markets/" + closedMarket.getId() + "/scrap").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		MvcResult result = mockMvc
				.perform(get("/api/users/me/scraps").param("excludeClosed", "true").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(1))
				.andReturn();
		assertThat(dataOf(result).get("items").get(0).get("id").asLong()).isEqualTo(openMarket.getId());
	}

	@Test
	void myScraps_cursorPagination_noDuplicatesOrGaps() throws Exception {
		User scraper = createAgreedUser("scraper12");
		String token = tokenFor(scraper);
		Set<Long> scrappedIds = new HashSet<>();
		for (int i = 0; i < 5; i++) {
			// Self-scrap isn't allowed, so the scraper can't own the market being scrapped.
			Market market = seedMarket(createAgreedUser("owner12-" + i), Category.ETC, false);
			mockMvc.perform(post("/api/markets/" + market.getId() + "/scrap").header("Authorization", "Bearer " + token))
					.andExpect(status().isOk());
			scrappedIds.add(market.getId());
		}

		Set<Long> collected = new HashSet<>();
		String cursor = null;
		boolean hasNext = true;
		int pages = 0;
		while (hasNext) {
			var requestBuilder = get("/api/users/me/scraps").param("size", "2").header("Authorization", "Bearer " + token);
			if (cursor != null) {
				requestBuilder = requestBuilder.param("cursor", cursor);
			}
			MvcResult result = mockMvc.perform(requestBuilder).andExpect(status().isOk()).andReturn();
			JsonNode data = dataOf(result);
			data.get("items").forEach(item -> collected.add(item.get("id").asLong()));
			hasNext = data.get("hasNext").asBoolean();
			cursor = hasNext ? data.get("nextCursor").asText() : null;
			pages++;
			if (pages > 10) {
				throw new AssertionError("too many pages, pagination likely broken");
			}
		}

		assertThat(pages).isEqualTo(3);
		assertThat(collected).isEqualTo(scrappedIds);
	}

	@Test
	void withdraw_decrementsScrapCountOfScrapedMarketsByExactlyOne() throws Exception {
		User marketOwner = createAgreedUser("owner13");
		User otherScraper = createAgreedUser("otherScraper13");
		User withdrawingUser = createAgreedUser("withdrawing13");
		Market market = seedMarket(marketOwner, Category.ETC, false);

		mockMvc.perform(post("/api/markets/" + market.getId() + "/scrap").header("Authorization", "Bearer " + tokenFor(otherScraper)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/markets/" + market.getId() + "/scrap").header("Authorization", "Bearer " + tokenFor(withdrawingUser)))
				.andExpect(status().isOk());
		assertThat(marketRepository.findById(market.getId()).orElseThrow().getScrapCount()).isEqualTo(2);

		mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + tokenFor(withdrawingUser)))
				.andExpect(status().isOk());

		assertThat(marketRepository.findById(market.getId()).orElseThrow().getScrapCount()).isEqualTo(1);
		assertThat(scrapRepository.existsByUserIdAndMarketId(otherScraper.getId(), market.getId())).isTrue();
		assertThat(scrapRepository.existsByUserIdAndMarketId(withdrawingUser.getId(), market.getId())).isFalse();
	}

	// ---- helpers ----

	private Market seedMarket(User owner, Category category, boolean closed) {
		Market market = marketRepository.save(Market.builder()
				.user(owner)
				.category(category)
				.title("title")
				.itemCategories("cats")
				.description("desc")
				.build());
		if (closed) {
			market.update(category, market.getTitle(), market.getItemCategories(), market.getDescription(), true);
		}
		return market;
	}

	private User createAgreedUser(String label) {
		// nickname is varchar(50) — an 8-char suffix keeps label+suffix well under
		// that even for the longest labels used in this file, unlike a full UUID.
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		return userRepository.save(User.builder()
				.provider(Provider.GOOGLE)
				.providerId(label + "-" + suffix)
				.nickname(label + "-" + suffix)
				.termsAgreedAt(LocalDateTime.now())
				.build());
	}

	private String tokenFor(User user) {
		return jwtTokenProvider.generateAccessToken(user.getId());
	}

	private JsonNode dataOf(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private JsonNode findById(JsonNode items, Long id) {
		return StreamSupport.stream(items.spliterator(), false)
				.filter(item -> item.get("id").asLong() == id)
				.findFirst()
				.orElseThrow(() -> new AssertionError("market " + id + " not found in response"));
	}
}
