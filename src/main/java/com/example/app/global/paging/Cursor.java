package com.example.app.global.paging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

// "{createdAt epoch millis}_{id}", base64-encoded. Kept free of any ErrorCode /
// BusinessException dependency so it stays reusable by other cursor-paginated
// domains — callers translate a decode failure into their own error code.
public record Cursor(LocalDateTime createdAt, Long id) {

	private static final ZoneId ZONE = ZoneId.systemDefault();

	public static Cursor decode(String encoded) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			int separatorIndex = raw.indexOf('_');
			long epochMillis = Long.parseLong(raw.substring(0, separatorIndex));
			long id = Long.parseLong(raw.substring(separatorIndex + 1));
			return new Cursor(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZONE), id);
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid cursor: " + encoded, e);
		}
	}

	public String encode() {
		long epochMillis = createdAt.atZone(ZONE).toInstant().toEpochMilli();
		String raw = epochMillis + "_" + id;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}
}
