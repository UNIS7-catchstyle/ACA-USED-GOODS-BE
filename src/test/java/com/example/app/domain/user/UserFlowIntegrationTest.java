package com.example.app.domain.user;

import com.example.app.domain.auth.entity.Provider;
import com.example.app.domain.comment.entity.Comment;
import com.example.app.domain.comment.repository.CommentRepository;
import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import com.example.app.domain.market.entity.MarketImage;
import com.example.app.domain.market.repository.MarketImageRepository;
import com.example.app.domain.market.repository.MarketRepository;
import com.example.app.domain.scrap.entity.Scrap;
import com.example.app.domain.scrap.repository.ScrapRepository;
import com.example.app.domain.user.entity.User;
import com.example.app.domain.user.repository.RefreshTokenRepository;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.global.exception.ErrorCode;
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
class UserFlowIntegrationTest {

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
	private CommentRepository commentRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Test
	void termsAgreementFlow() throws Exception {
		JsonNode login = login();
		String accessToken = login.get("accessToken").asText();
		Long userId = userRepository.findAll().get(0).getId();
		String auth = "Bearer " + accessToken;

		// 1. before agreement: GET /api/users/me still works, termsAgreed=false, hasMarket=false
		mockMvc.perform(get("/api/users/me").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.termsAgreed").value(false))
				.andExpect(jsonPath("$.data.hasMarket").value(false))
				.andExpect(jsonPath("$.data.marketCount").value(0));

		// 5. not agreed -> POST /api/markets -> 403 TERMS_NOT_AGREED
		mockMvc.perform(post("/api/markets").header("Authorization", auth))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ErrorCode.TERMS_NOT_AGREED.getCode()));

		// 7. auth-optional GET is never blocked by the terms check
		mockMvc.perform(get("/api/markets").param("category", "KPOP").header("Authorization", auth))
				.andExpect(status().isOk());

		// 8. /api/auth/** is terms-exempt
		mockMvc.perform(post("/api/auth/logout").header("Authorization", auth))
				.andExpect(status().isOk());

		// 2. requiredAgreed=false -> 400, terms_agreed_at stays null
		mockMvc.perform(post("/api/users/me/terms")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(termsBody(false, false, true)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.TERMS_REQUIRED_NOT_AGREED.getCode()));
		assertThat(userRepository.findById(userId).orElseThrow().getTermsAgreedAt()).isNull();

		// 3. requiredAgreed=true -> 200, terms_agreed_at set, marketing flags stored
		mockMvc.perform(post("/api/users/me/terms")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(termsBody(true, false, true)))
				.andExpect(status().isOk());
		User afterFirstAgreement = userRepository.findById(userId).orElseThrow();
		assertThat(afterFirstAgreement.getTermsAgreedAt()).isNotNull();
		assertThat(afterFirstAgreement.isMarketingEmailAgreed()).isFalse();
		assertThat(afterFirstAgreement.isMarketingSnsAgreed()).isTrue();
		var firstAgreedAt = afterFirstAgreement.getTermsAgreedAt();

		// 4. re-agree -> terms_agreed_at unchanged, marketing flags updated
		mockMvc.perform(post("/api/users/me/terms")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(termsBody(true, true, false)))
				.andExpect(status().isOk());
		User afterSecondAgreement = userRepository.findById(userId).orElseThrow();
		assertThat(afterSecondAgreement.getTermsAgreedAt()).isEqualTo(firstAgreedAt);
		assertThat(afterSecondAgreement.isMarketingEmailAgreed()).isTrue();
		assertThat(afterSecondAgreement.isMarketingSnsAgreed()).isFalse();

		// 6. agreed -> POST /api/markets passes the interceptor and reaches the real handler
		mockMvc.perform(post("/api/markets")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"category\":\"ETC\",\"title\":\"t\",\"itemCategories\":\"c\",\"description\":\"d\"}"))
				.andExpect(status().isCreated());

		// same user can register a second market — no more one-per-user limit
		mockMvc.perform(post("/api/markets")
						.header("Authorization", auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"category\":\"KPOP\",\"title\":\"t2\",\"itemCategories\":\"c\",\"description\":\"d\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/users/me").header("Authorization", auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasMarket").value(true))
				.andExpect(jsonPath("$.data.marketCount").value(2));

		// 9. re-login after agreeing -> needsTermsAgreement=false
		JsonNode reLogin = login();
		assertThat(reLogin.get("needsTermsAgreement").asBoolean()).isFalse();
	}

	@Test
	void withdrawalFlow() throws Exception {
		JsonNode login = login();
		String accessToken = login.get("accessToken").asText();
		User userA = userRepository.findAll().get(0);
		Long userAId = userA.getId();

		User userB = userRepository.save(User.builder()
				.provider(Provider.GOOGLE)
				.providerId("other-provider-id")
				.nickname("other user")
				.build());

		// userA now owns two markets: both are deleted wholesale on withdrawal,
		// including every comment tied to them (own or not) and every scrap of
		// them (own or not).
		Market marketA = marketRepository.save(Market.builder()
				.user(userA)
				.category(Category.ETC)
				.title("userA's market")
				.itemCategories("goods")
				.description("market description")
				.build());
		marketImageRepository.save(MarketImage.builder()
				.market(marketA)
				.imageUrl("https://example.com/image.png")
				.sortOrder(0)
				.build());
		scrapRepository.save(Scrap.builder().user(userA).market(marketA).build());
		scrapRepository.save(Scrap.builder().user(userB).market(marketA).build());
		Comment commentOnOwnMarket = commentRepository.save(Comment.builder()
				.market(marketA).user(userA).content("userA on their own market").build());
		Comment otherUserCommentOnOwnMarket = commentRepository.save(Comment.builder()
				.market(marketA).user(userB).content("userB on userA's market").build());

		Market marketA2 = marketRepository.save(Market.builder()
				.user(userA)
				.category(Category.KPOP)
				.title("userA's second market")
				.itemCategories("goods")
				.description("market description")
				.build());
		marketImageRepository.save(MarketImage.builder()
				.market(marketA2)
				.imageUrl("https://example.com/image2.png")
				.sortOrder(0)
				.build());
		scrapRepository.save(Scrap.builder().user(userB).market(marketA2).build());
		Comment commentOnSecondMarket = commentRepository.save(Comment.builder()
				.market(marketA2).user(userB).content("userB on userA's second market").build());

		// userB's market: survives. userA's own comment there is left exactly as
		// posted (content/image_url untouched) — only the User row is redacted —
		// so userB's reply keeps a valid parent_id either way.
		Market marketB = marketRepository.save(Market.builder()
				.user(userB)
				.category(Category.ETC)
				.title("userB's market")
				.itemCategories("goods")
				.description("market description")
				.build());
		Comment commentOnOtherMarket = commentRepository.save(Comment.builder()
				.market(marketB).user(userA).content("userA on userB's market")
				.imageUrl("https://example.com/comment-image.png").build());
		Comment replyToRedactedComment = commentRepository.save(Comment.builder()
				.market(marketB).user(userB).parent(commentOnOtherMarket).content("userB's reply").build());

		// userA also scraps userB's (surviving) market. Regression coverage for a bug
		// where scrapService.deleteAllByUser()'s clearAutomatically bulk update (run
		// to decrement marketB's scrap_count) detached the already-loaded User entity,
		// silently dropping the softDelete() that runs afterward.
		scrapRepository.save(Scrap.builder().user(userA).market(marketB).build());
		marketRepository.incrementScrapCount(marketB.getId());

		assertThat(refreshTokenRepository.findByUserId(userAId)).isPresent();

		// 10 & 11. DELETE /api/users/me
		mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		assertThat(marketRepository.findByUserId(userAId)).isEmpty();
		assertThat(marketRepository.findById(marketA.getId())).isEmpty();
		assertThat(marketRepository.findById(marketA2.getId())).isEmpty();
		assertThat(marketRepository.findById(marketB.getId())).isPresent();
		assertThat(marketImageRepository.count()).isZero();
		assertThat(scrapRepository.count()).isZero();
		assertThat(marketRepository.findById(marketB.getId()).orElseThrow().getScrapCount()).isZero();
		assertThat(refreshTokenRepository.findByUserId(userAId)).isEmpty();

		// comments tied to either deleted market are gone entirely, regardless of author
		assertThat(commentRepository.findById(commentOnOwnMarket.getId())).isEmpty();
		assertThat(commentRepository.findById(otherUserCommentOnOwnMarket.getId())).isEmpty();
		assertThat(commentRepository.findById(commentOnSecondMarket.getId())).isEmpty();

		// userA's comment on a surviving market is left as-is, not redacted or deleted —
		// only the author's User row is soft-deleted (nickname substitution happens there).
		Comment reloadedComment = commentRepository.findById(commentOnOtherMarket.getId()).orElseThrow();
		assertThat(reloadedComment.getContent()).isEqualTo("userA on userB's market");
		assertThat(reloadedComment.getImageUrl()).isEqualTo("https://example.com/comment-image.png");

		// so userB's reply to it keeps a valid, unaffected parent_id
		Comment reloadedReply = commentRepository.findById(replyToRedactedComment.getId()).orElseThrow();
		assertThat(reloadedReply.getContent()).isEqualTo("userB's reply");
		assertThat(reloadedReply.getParent().getId()).isEqualTo(commentOnOtherMarket.getId());

		User softDeletedUserA = userRepository.findById(userAId).orElseThrow();
		assertThat(softDeletedUserA.getDeletedAt()).isNotNull();
		assertThat(softDeletedUserA.getNickname()).isEqualTo("탈퇴한 사용자");
		assertThat(softDeletedUserA.getProfileImageUrl()).isNull();
		assertThat(softDeletedUserA.getProviderId()).isEqualTo("deleted_" + userAId);

		// same (still cryptographically valid) token -> 401, not 404
		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

		// re-login with the same providerId -> treated as a brand new user
		JsonNode reLogin = login();
		assertThat(reLogin.get("isNewUser").asBoolean()).isTrue();
		assertThat(userRepository.count()).isEqualTo(3); // deleted userA + userB + new userA
	}

	private JsonNode login() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"accessToken\":\"any\"}"))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private String termsBody(boolean requiredAgreed, boolean marketingEmailAgreed, boolean marketingSnsAgreed) throws Exception {
		return objectMapper.writeValueAsString(new TermsBody(requiredAgreed, marketingEmailAgreed, marketingSnsAgreed));
	}

	private record TermsBody(boolean requiredAgreed, boolean marketingEmailAgreed, boolean marketingSnsAgreed) {
	}
}
