package com.example.app.domain.comment;

import com.example.app.domain.auth.entity.Provider;
import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.domain.user.entity.User;
import com.example.app.domain.user.repository.UserRepository;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MyPageIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MarketRepository marketRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void myMarkets_noMarket_returnsEmptyArray() throws Exception {
		User user = createAgreedUser("nomarket14");

		mockMvc.perform(get("/api/users/me/markets").header("Authorization", "Bearer " + tokenFor(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void myMarkets_twoMarkets_returnsBothIncludingClosedOnes() throws Exception {
		User owner = createAgreedUser("owner15");
		Market marketA = seedMarket(owner, Category.ETC, false);
		Market marketB = seedMarket(owner, Category.ETC, true);

		MvcResult result = mockMvc.perform(get("/api/users/me/markets").header("Authorization", "Bearer " + tokenFor(owner)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andReturn();

		JsonNode items = dataOf(result);
		Set<Long> ids = new HashSet<>();
		items.forEach(item -> ids.add(item.get("id").asLong()));
		assertThat(ids).containsExactlyInAnyOrder(marketA.getId(), marketB.getId());
	}

	@Test
	void commentedMarkets_orderedByMostRecentCommentAndDedupedPerMarket() throws Exception {
		User commenter = createAgreedUser("commenter16");
		String token = tokenFor(commenter);
		Market marketA = seedMarket(createAgreedUser("ownerA16"), Category.ETC, false);
		Market marketB = seedMarket(createAgreedUser("ownerB16"), Category.ETC, false);
		seedMarket(createAgreedUser("ownerC16"), Category.ETC, false); // C: no comments, must not appear

		// B first, then A three times, so A's latest comment has the highest id overall.
		postComment(marketB.getId(), token, "b1");
		postComment(marketA.getId(), token, "a1");
		postComment(marketA.getId(), token, "a2");
		postComment(marketA.getId(), token, "a3");

		MvcResult result = mockMvc.perform(get("/api/users/me/commented-markets").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(2))
				.andReturn();
		JsonNode items = dataOf(result).get("items");
		assertThat(items).hasSize(2);
		assertThat(items.get(0).get("id").asLong()).isEqualTo(marketA.getId());
		assertThat(items.get(1).get("id").asLong()).isEqualTo(marketB.getId());
	}

	@Test
	void commentedMarkets_newCommentOnOlderMarket_movesItToFront() throws Exception {
		User commenter = createAgreedUser("commenter17");
		String token = tokenFor(commenter);
		Market marketA = seedMarket(createAgreedUser("ownerA17"), Category.ETC, false);
		Market marketB = seedMarket(createAgreedUser("ownerB17"), Category.ETC, false);

		postComment(marketB.getId(), token, "b1");
		postComment(marketA.getId(), token, "a1");

		postComment(marketB.getId(), token, "b2-new");

		MvcResult result = mockMvc.perform(get("/api/users/me/commented-markets").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode items = dataOf(result).get("items");
		assertThat(items.get(0).get("id").asLong()).isEqualTo(marketB.getId());
		assertThat(items.get(1).get("id").asLong()).isEqualTo(marketA.getId());
	}

	@Test
	void commentedMarkets_excludeClosed_excludesClosedMarkets() throws Exception {
		User commenter = createAgreedUser("commenter18");
		String token = tokenFor(commenter);
		Market openMarket = seedMarket(createAgreedUser("ownerOpen18"), Category.ETC, false);
		Market closedMarket = seedMarket(createAgreedUser("ownerClosed18"), Category.ETC, true);
		postComment(openMarket.getId(), token, "on open");
		postComment(closedMarket.getId(), token, "on closed");

		MvcResult result = mockMvc.perform(get("/api/users/me/commented-markets")
						.param("excludeClosed", "true")
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(1))
				.andReturn();
		assertThat(dataOf(result).get("items").get(0).get("id").asLong()).isEqualTo(openMarket.getId());
	}

	@Test
	void commentedMarkets_cursorPagination_noDuplicatesOrGaps() throws Exception {
		User commenter = createAgreedUser("commenter19");
		String token = tokenFor(commenter);
		Set<Long> expectedIds = new HashSet<>();
		for (int i = 0; i < 5; i++) {
			Market market = seedMarket(createAgreedUser("owner19-" + i), Category.ETC, false);
			postComment(market.getId(), token, "c1-" + i);
			postComment(market.getId(), token, "c2-" + i);
			expectedIds.add(market.getId());
		}

		Set<Long> collected = new HashSet<>();
		String cursor = null;
		boolean hasNext = true;
		int pages = 0;
		while (hasNext) {
			var requestBuilder = get("/api/users/me/commented-markets").param("size", "2").header("Authorization", "Bearer " + token);
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
		assertThat(collected).isEqualTo(expectedIds);
	}

	@Test
	void commentedMarkets_noComments_returnsEmptyTotalCountZero() throws Exception {
		User commenter = createAgreedUser("commenter20");

		mockMvc.perform(get("/api/users/me/commented-markets").header("Authorization", "Bearer " + tokenFor(commenter)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalCount").value(0))
				.andExpect(jsonPath("$.data.items.length()").value(0));
	}

	// ---- helpers ----

	private void postComment(Long marketId, String token, String content) throws Exception {
		mockMvc.perform(post("/api/markets/" + marketId + "/comments")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"" + content + "\"}"))
				.andExpect(status().isCreated());
	}

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
}
