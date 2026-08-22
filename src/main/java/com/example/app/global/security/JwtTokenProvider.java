package com.example.app.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

	private static final String CLAIM_TYPE = "type";
	private static final String TOKEN_TYPE_ACCESS = "access";
	private static final String TOKEN_TYPE_REFRESH = "refresh";

	private final SecretKey key;
	private final long accessTokenExpirationMillis;
	private final long refreshTokenExpirationMillis;

	public JwtTokenProvider(
			@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-expiration}") long accessTokenExpirationMillis,
			@Value("${jwt.refresh-token-expiration}") long refreshTokenExpirationMillis) {
		this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
		this.accessTokenExpirationMillis = accessTokenExpirationMillis;
		this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
	}

	public String generateAccessToken(Long userId) {
		Date expiry = new Date(System.currentTimeMillis() + accessTokenExpirationMillis);
		return buildToken(userId, TOKEN_TYPE_ACCESS, expiry, null);
	}

	public IssuedToken generateRefreshToken(Long userId) {
		Date expiry = new Date(System.currentTimeMillis() + refreshTokenExpirationMillis);
		String token = buildToken(userId, TOKEN_TYPE_REFRESH, expiry, UUID.randomUUID().toString());
		LocalDateTime expiresAt = LocalDateTime.ofInstant(expiry.toInstant(), ZoneId.systemDefault());
		return new IssuedToken(token, expiresAt);
	}

	public TokenStatus validate(String token) {
		try {
			parseClaims(token);
			return TokenStatus.VALID;
		} catch (ExpiredJwtException e) {
			return TokenStatus.EXPIRED;
		} catch (JwtException | IllegalArgumentException e) {
			return TokenStatus.INVALID;
		}
	}

	public Long getUserId(String token) {
		return Long.valueOf(parseClaims(token).getSubject());
	}

	public boolean isAccessToken(String token) {
		return TOKEN_TYPE_ACCESS.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
	}

	public boolean isRefreshToken(String token) {
		return TOKEN_TYPE_REFRESH.equals(parseClaims(token).get(CLAIM_TYPE, String.class));
	}

	private String buildToken(Long userId, String type, Date expiry, String jti) {
		JwtBuilder builder = Jwts.builder()
				.subject(String.valueOf(userId))
				.claim(CLAIM_TYPE, type)
				.issuedAt(new Date())
				.expiration(expiry);
		if (jti != null) {
			builder.id(jti);
		}
		return builder.signWith(key).compact();
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public enum TokenStatus {
		VALID,
		EXPIRED,
		INVALID
	}

	public record IssuedToken(String token, LocalDateTime expiresAt) {
	}
}
