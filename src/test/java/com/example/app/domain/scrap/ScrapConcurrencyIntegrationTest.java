package com.example.app.domain.scrap;

import com.example.app.domain.auth.entity.Provider;
import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.domain.scrap.repository.ScrapRepository;
import com.example.app.domain.user.entity.User;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Deliberately NOT @Transactional: Spring's test-managed transaction is bound to the
// main test thread only. Worker threads dispatching concurrent MockMvc requests each
// run their own independent, genuinely-committing transaction regardless, so wrapping
// this test in one would just create a mismatch between what the main thread can see
// and what workers actually committed. Isolated into its own H2 instance/context via
// @DynamicPropertySource (same trick as MarketImageCleanupIntegrationTest) so it can't
// collide with other test classes' data.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScrapConcurrencyIntegrationTest {

	@TempDir
	static Path baseDir;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("storage.local.base-dir", baseDir::toString);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MarketRepository marketRepository;

	@Autowired
	private ScrapRepository scrapRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Test
	void tenUsersScrapTheSameMarketConcurrently_countEndsAtTenWithTenRows() throws Exception {
		User owner = createAgreedUser("owner");
		Market market = marketRepository.save(Market.builder()
				.user(owner).category(Category.ETC).title("t").itemCategories("c").description("d").build());

		int threadCount = 10;
		List<String> tokens = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			tokens.add(jwtTokenProvider.generateAccessToken(createAgreedUser("scraper" + i).getId()));
		}

		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> futures = new ArrayList<>();
		for (String token : tokens) {
			futures.add(executor.submit(() -> {
				start.await();
				return mockMvc.perform(post("/api/markets/" + market.getId() + "/scrap")
								.header("Authorization", "Bearer " + token))
						.andReturn().getResponse().getStatus();
			}));
		}
		start.countDown();
		for (Future<Integer> future : futures) {
			assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(200);
		}
		executor.shutdown();

		assertThat(marketRepository.findById(market.getId()).orElseThrow().getScrapCount()).isEqualTo(threadCount);
		assertThat(scrapRepository.count()).isEqualTo(threadCount);
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
}
