package com.example.app.global.paging;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// "{key1}_{key2}", base64url-encoded. Generic over two longs rather than tied to
// (createdAt, id): every cursor-paginated query here sorts by two fields with a
// tie-break, but the leading field isn't always a timestamp (e.g. commented-markets
// sorts by MAX(comment.id), not a date) — callers convert their own sort keys
// (a LocalDateTime -> epoch millis, an id -> itself) to/from longs. Kept free of any
// ErrorCode/BusinessException dependency so it stays reusable — callers translate a
// decode failure into their own error code.
public record Cursor(long key1, long key2) {

	public static Cursor decode(String encoded) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			int separatorIndex = raw.indexOf('_');
			long key1 = Long.parseLong(raw.substring(0, separatorIndex));
			long key2 = Long.parseLong(raw.substring(separatorIndex + 1));
			return new Cursor(key1, key2);
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid cursor: " + encoded, e);
		}
	}

	public String encode() {
		String raw = key1 + "_" + key2;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}
}
