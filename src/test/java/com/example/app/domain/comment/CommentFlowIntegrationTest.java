package com.example.app.domain.comment;

import com.example.app.domain.auth.entity.Provider;
import com.example.app.domain.market.entity.Category;
import com.example.app.domain.market.entity.Market;
import com.example.app.domain.market.repository.MarketRepository;
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
import java.util.UUID;

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
class CommentFlowIntegrationTest {

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
	void getComments_emptyMarket_returns200EmptyArray() throws Exception {
		Market market = seedMarket(createAgreedUser("owner1"));

		mockMvc.perform(get("/api/markets/" + market.getId() + "/comments"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isArray())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void getComments_threeLevelTree_correctStructureAndIdAscOrder() throws Exception {
		User owner = createAgreedUser("owner2");
		Market market = seedMarket(owner);
		String token = tokenFor(owner);

		Long root1 = postComment(market.getId(), token, "root1", null);
		Long root2 = postComment(market.getId(), token, "root2", null);
		Long reply1 = postComment(market.getId(), token, "reply1-of-root1", root1);
		Long reply2 = postComment(market.getId(), token, "reply2-of-root1", root1);
		Long grandchild = postComment(market.getId(), token, "child-of-reply1", reply1);

		MvcResult result = mockMvc.perform(get("/api/markets/" + market.getId() + "/comments"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode tree = dataArray(result);

		assertThat(tree).hasSize(2);
		assertThat(tree.get(0).get("id").asLong()).isEqualTo(root1);
		assertThat(tree.get(1).get("id").asLong()).isEqualTo(root2);

		JsonNode root1Children = tree.get(0).get("children");
		assertThat(root1Children).hasSize(2);
		assertThat(root1Children.get(0).get("id").asLong()).isEqualTo(reply1);
		assertThat(root1Children.get(1).get("id").asLong()).isEqualTo(reply2);
		assertThat(root1Children.get(1).get("children")).isEmpty();

		JsonNode reply1Children = root1Children.get(0).get("children");
		assertThat(reply1Children).hasSize(1);
		assertThat(reply1Children.get(0).get("id").asLong()).isEqualTo(grandchild);
		assertThat(reply1Children.get(0).get("children")).isEmpty();
		assertThat(tree.get(1).get("children")).isEmpty();
	}

	@Test
	void getDetail_includesSameCommentTree() throws Exception {
		User owner = createAgreedUser("owner3");
		Market market = seedMarket(owner);
		String token = tokenFor(owner);
		Long root = postComment(market.getId(), token, "root", null);
		postComment(market.getId(), token, "reply", root);

		MvcResult result = mockMvc.perform(get("/api/markets/" + market.getId()))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode comments = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("comments");

		assertThat(comments).hasSize(1);
		assertThat(comments.get(0).get("id").asLong()).isEqualTo(root);
		assertThat(comments.get(0).get("children")).hasSize(1);
	}

	@Test
	void getComments_anonymous_returns200() throws Exception {
		Market market = seedMarket(createAgreedUser("owner5"));

		mockMvc.perform(get("/api/markets/" + market.getId() + "/comments"))
				.andExpect(status().isOk());
	}

	@Test
	void postComment_root_returns201WithNullParent() throws Exception {
		User owner = createAgreedUser("owner6");
		Market market = seedMarket(owner);

		MvcResult result = mockMvc.perform(post("/api/markets/" + market.getId() + "/comments")
						.header("Authorization", "Bearer " + tokenFor(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"hello\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.content").value("hello"))
				.andExpect(jsonPath("$.data.children").isArray())
				.andExpect(jsonPath("$.data.children.length()").value(0))
				.andReturn();
		Long id = dataOf(result).get("id").asLong();

		MvcResult treeResult = mockMvc.perform(get("/api/markets/" + market.getId() + "/comments"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode tree = dataArray(treeResult);
		assertThat(tree.get(0).get("id").asLong()).isEqualTo(id);
	}

	@Test
	void postComment_reply_returns201AndAppearsInTreeChildren() throws Exception {
		User owner = createAgreedUser("owner7");
		Market market = seedMarket(owner);
		String token = tokenFor(owner);
		Long root = postComment(market.getId(), token, "root", null);

		MvcResult result = mockMvc.perform(post("/api/markets/" + market.getId() + "/comments")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"a reply\",\"parentId\":" + root + "}"))
				.andExpect(status().isCreated())
				.andReturn();
		Long replyId = dataOf(result).get("id").asLong();

		MvcResult treeResult = mockMvc.perform(get("/api/markets/" + market.getId() + "/comments"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode children = dataArray(treeResult).get(0).get("children");
		assertThat(children).hasSize(1);
		assertThat(children.get(0).get("id").asLong()).isEqualTo(replyId);
	}

	@Test
	void postComment_parentFromDifferentMarket_returns400InvalidParentComment() throws Exception {
		User owner = createAgreedUser("owner8");
		Market marketA = seedMarket(owner);
		Market marketB = seedMarket(createAgreedUser("owner8b"));
		String token = tokenFor(owner);
		Long commentOnA = postComment(marketA.getId(), token, "on A", null);

		mockMvc.perform(post("/api/markets/" + marketB.getId() + "/comments")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"cross-market reply\",\"parentId\":" + commentOnA + "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARENT_COMMENT.getCode()));
	}

	@Test
	void postComment_nonExistentParent_returns400InvalidParentComment() throws Exception {
		User owner = createAgreedUser("owner9");
		Market market = seedMarket(owner);

		mockMvc.perform(post("/api/markets/" + market.getId() + "/comments")
						.header("Authorization", "Bearer " + tokenFor(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"orphan\",\"parentId\":999999999}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PARENT_COMMENT.getCode()));
	}

	@Test
	void postComment_externalImageUrl_returns400_ownStorageUrl_returns201() throws Exception {
		User owner = createAgreedUser("owner10");
		Market market = seedMarket(owner);
		String token = tokenFor(owner);

		mockMvc.perform(post("/api/markets/" + market.getId() + "/comments")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"has image\",\"imageUrl\":\"https://evil.example.com/a.png\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_IMAGE_URL.getCode()));

		mockMvc.perform(post("/api/markets/" + market.getId() + "/comments")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"has image\",\"imageUrl\":\"http://localhost:8080/uploads/a.png\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.imageUrl").value("http://localhost:8080/uploads/a.png"));
	}

	@Test
	void postComment_contentTooLongOrBlank_returns400() throws Exception {
		User owner = createAgreedUser("owner11");
		Market market = seedMarket(owner);
		String token = tokenFor(owner);

		mockMvc.perform(post("/api/markets/" + market.getId() + "/comments")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"" + "a".repeat(1001) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

		mockMvc.perform(post("/api/markets/" + market.getId() + "/comments")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
	}

	@Test
	void postComment_noToken_returns401_termsNotAgreed_returns403() throws Exception {
		User owner = createAgreedUser("owner12");
		Market market = seedMarket(owner);

		mockMvc.perform(post("/api/markets/" + market.getId() + "/comments")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"hi\"}"))
				.andExpect(status().isUnauthorized());

		User notAgreed = userRepository.save(User.builder()
				.provider(Provider.GOOGLE)
				.providerId("not-agreed-12-" + UUID.randomUUID())
				.nickname("not-agreed-12")
				.build());
		mockMvc.perform(post("/api/markets/" + market.getId() + "/comments")
						.header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(notAgreed.getId()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"hi\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value(ErrorCode.TERMS_NOT_AGREED.getCode()));
	}

	@Test
	void postComment_onClosedMarket_returns201() throws Exception {
		User owner = createAgreedUser("owner13");
		Market market = seedMarket(owner);
		market.update(market.getCategory(), market.getTitle(), market.getItemCategories(), market.getDescription(), true);

		mockMvc.perform(post("/api/markets/" + market.getId() + "/comments")
						.header("Authorization", "Bearer " + tokenFor(owner))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"still allowed\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void withdrawnAuthor_commentTreeShowsRedactedNicknameAndContent() throws Exception {
		User owner = createAgreedUser("owner21");
		Market market = seedMarket(owner);
		User author = createAgreedUser("author21");
		String authorToken = tokenFor(author);
		Long commentId = postComment(market.getId(), authorToken, "will be redacted", null);

		mockMvc.perform(delete("/api/users/me").header("Authorization", "Bearer " + authorToken))
				.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(get("/api/markets/" + market.getId() + "/comments"))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode node = dataArray(result).get(0);
		assertThat(node.get("id").asLong()).isEqualTo(commentId);
		assertThat(node.get("author").get("nickname").asText()).isEqualTo("탈퇴한 사용자");
		assertThat(node.get("content").asText()).isEqualTo("삭제된 댓글입니다");
	}

	// ---- helpers ----

	private Long postComment(Long marketId, String token, String content, Long parentId) throws Exception {
		String parentField = parentId == null ? "" : ",\"parentId\":" + parentId;
		MvcResult result = mockMvc.perform(post("/api/markets/" + marketId + "/comments")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"" + content + "\"" + parentField + "}"))
				.andExpect(status().isCreated())
				.andReturn();
		return dataOf(result).get("id").asLong();
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

	private JsonNode dataOf(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private JsonNode dataArray(MvcResult result) throws Exception {
		return dataOf(result);
	}
}
