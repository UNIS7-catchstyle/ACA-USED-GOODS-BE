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
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Proves the tree fetch is N+1-free without hardcoding an exact query count (which
// would depend on Spring Data's internal implementation of existsById, not something
// worth pinning down): a market with 1 comment and one with 11 comments across 3
// levels must cost the identical number of queries.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommentQueryCountIntegrationTest {

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
	}

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

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Test
	void getComments_queryCountDoesNotScaleWithCommentCount() throws Exception {
		User owner1 = createAgreedUser("cqc-owner1");
		Market smallMarket = seedMarket(owner1);
		postComment(smallMarket.getId(), tokenFor(owner1), "only one", null);

		User owner2 = createAgreedUser("cqc-owner2");
		Market bigMarket = seedMarket(owner2);
		String bigToken = tokenFor(owner2);
		Long root = postComment(bigMarket.getId(), bigToken, "root", null);
		for (int i = 0; i < 5; i++) {
			Long child = postComment(bigMarket.getId(), bigToken, "child" + i, root);
			postComment(bigMarket.getId(), bigToken, "grandchild" + i, child);
		}

		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

		statistics.clear();
		mockMvc.perform(get("/api/markets/" + smallMarket.getId() + "/comments")).andExpect(status().isOk());
		long smallTreeQueryCount = statistics.getQueryExecutionCount();

		statistics.clear();
		MvcResult bigTreeResult = mockMvc.perform(get("/api/markets/" + bigMarket.getId() + "/comments"))
				.andExpect(status().isOk())
				.andReturn();
		long bigTreeQueryCount = statistics.getQueryExecutionCount();

		JsonNode bigTree = objectMapper.readTree(bigTreeResult.getResponse().getContentAsString()).get("data");
		assertThat(bigTree).hasSize(1); // sanity check: the 11-comment tree actually got built
		assertThat(bigTreeQueryCount)
				.as("query count for an 11-comment/3-level tree must equal a 1-comment tree's — no N+1")
				.isEqualTo(smallTreeQueryCount);
		assertThat(bigTreeQueryCount).isLessThanOrEqualTo(3);
	}

	private Long postComment(Long marketId, String token, String content, Long parentId) throws Exception {
		String parentField = parentId == null ? "" : ",\"parentId\":" + parentId;
		MvcResult result = mockMvc.perform(post("/api/markets/" + marketId + "/comments")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"" + content + "\"" + parentField + "}"))
				.andExpect(status().isCreated())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
	}

	private Market seedMarket(User owner) {
		return marketRepository.save(Market.builder()
				.user(owner)
				.category(Category.ETC)
				.title("title")
				.itemCategories("cats")
				.description("desc")
				.build());
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
}
