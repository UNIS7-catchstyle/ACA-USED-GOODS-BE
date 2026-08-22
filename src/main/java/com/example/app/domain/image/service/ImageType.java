package com.example.app.domain.image.service;

import java.util.Optional;

/**
 * Known image types, identified by file signature ("magic number") rather than
 * trusting the client-supplied extension/Content-Type.
 */
public enum ImageType {

	JPG("jpg"),
	PNG("png"),
	GIF("gif"),
	WEBP("webp");

	private final String extension;

	ImageType(String extension) {
		this.extension = extension;
	}

	public String extension() {
		return extension;
	}

	public static Optional<ImageType> detect(byte[] header) {
		if (matches(header, 0, 0xFF, 0xD8, 0xFF)) {
			return Optional.of(JPG);
		}
		if (matches(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
			return Optional.of(PNG);
		}
		if (matches(header, 0, 'G', 'I', 'F', '8')) {
			return Optional.of(GIF);
		}
		if (matches(header, 0, 'R', 'I', 'F', 'F') && matches(header, 8, 'W', 'E', 'B', 'P')) {
			return Optional.of(WEBP);
		}
		return Optional.empty();
	}

	public static Optional<ImageType> fromExtension(String extension) {
		String normalized = normalizeExtension(extension);
		for (ImageType type : values()) {
			if (type.extension.equals(normalized)) {
				return Optional.of(type);
			}
		}
		return Optional.empty();
	}

	public static String normalizeExtension(String extension) {
		if (extension == null) {
			return null;
		}
		String lower = extension.toLowerCase();
		return "jpeg".equals(lower) ? "jpg" : lower;
	}

	private static boolean matches(byte[] header, int offset, int... expected) {
		if (header == null || header.length < offset + expected.length) {
			return false;
		}
		for (int i = 0; i < expected.length; i++) {
			if ((header[offset + i] & 0xFF) != expected[i]) {
				return false;
			}
		}
		return true;
	}
}
