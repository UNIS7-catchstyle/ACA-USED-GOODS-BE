package com.example.app.domain.auth;

import com.example.app.domain.user.entity.User;
import com.example.app.domain.user.repository.RefreshTokenRepository;
import com.example.app.domain.user.repository.UserRepository;
import com.example.app.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Value("${jwt.secret}")
	private String jwtSecret;

	@Test
	void authFlow() throws Exception {
		// 1. new login -> isNewUser=true, needsTermsAgreement=true, random nickname, one User+RefreshToken persisted
		JsonNode firstLogin = login();
		assertThat(firstLogin.get("isNewUser").asBoolean()).isTrue();
		assertThat(firstLogin.get("needsTermsAgreement").asBoolean()).isTrue();
		assertThat(firstLogin.get("accessToken").asText()).isNotBlank();
		assertThat(firstLogin.get("refreshToken").asText()).isNotBlank();

		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(refreshTokenRepository.count()).isEqualTo(1);
		User user = userRepository.findAll().get(0);
		assertThat(user.getNickname()).matches("^.+ .+\\d{4}$");

		String firstRefreshToken = firstLogin.get("refreshToken").asText();

		// 2. same providerId login again -> isNewUser=false, still one User, RefreshToken rotated
		JsonNode secondLogin = login();
		assertThat(secondLogin.get("isNewUser").asBoolean()).isFalse();
		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(refreshTokenRepository.findByUserId(user.getId()).orElseThrow().getToken())
				.isNotEqualTo(firstRefreshToken)
				.isEqualTo(secondLogin.get("refreshToken").asText());

		// 3. expired access token -> 401 TOKEN_EXPIRED
		String expiredAccessToken = buildExpiredAccessToken(user.getId());
		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expiredAccessToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorCode.TOKEN_EXPIRED.getCode()));

		// 4. reissue succeeds with two new tokens; reusing the old refresh token afterwards fails
		String currentRefreshToken = secondLogin.get("refreshToken").asText();
		JsonNode reissued = reissue(currentRefreshToken);
		String newAccessToken = reissued.get("accessToken").asText();
		String newRefreshToken = reissued.get("refreshToken").asText();
		assertThat(newRefreshToken).isNotEqualTo(currentRefreshToken);

		mockMvc.perform(post("/api/auth/reissue")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new ReissueBody(currentRefreshToken))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_TOKEN.getCode()));

		// 5. logout deletes the refresh token; reissuing the (still unexpired) refresh token afterwards fails
		mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + newAccessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		mockMvc.perform(post("/api/auth/reissue")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new ReissueBody(newRefreshToken))))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_TOKEN.getCode()));

		// 6. no token -> 401 UNAUTHORIZED
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.getCode()));

		// 7. unknown provider path -> 400 INVALID_INPUT_VALUE
		mockMvc.perform(post("/api/auth/login/naver")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginBody("any-token"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
	}

	@Test
	void garbageAuthorizationHeaderDoesNotBlockNoTokenPaths() throws Exception {
		// login/reissue need no token at all; a garbage Authorization header must not 401 them.
		mockMvc.perform(post("/api/auth/login/kakao")
						.header("Authorization", "Bearer garbage")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginBody("any-token"))))
				.andExpect(status().isOk());
	}

	@Test
	void duplicatedBearerPrefixIsTolerated() throws Exception {
		// Common Swagger "Authorize" mistake: pasting "Bearer <token>" into the value
		// field on top of swagger-ui's own auto-prefixing. Should still authenticate.
		String accessToken = login().get("accessToken").asText();

		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer Bearer " + accessToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));
	}

	private JsonNode login() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login/kakao")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginBody("any-token"))))
				.andExpect(status().isOk())
				.andReturn();
		return dataOf(result);
	}

	private JsonNode reissue(String refreshToken) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/reissue")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new ReissueBody(refreshToken))))
				.andExpect(status().isOk())
				.andReturn();
		return dataOf(result);
	}

	private JsonNode dataOf(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private String buildExpiredAccessToken(Long userId) {
		SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
		Date past = new Date(System.currentTimeMillis() - 10_000);
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("type", "access")
				.issuedAt(new Date(System.currentTimeMillis() - 20_000))
				.expiration(past)
				.signWith(key)
				.compact();
	}

	private record LoginBody(String accessToken) {
	}

	private record ReissueBody(String refreshToken) {
	}
}
