package com.vocactionops.backend.auth.token;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
public class RefreshTokenCodec {

	private static final int TOKEN_BYTES = 32;

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		byte[] tokenBytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(tokenBytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
	}

	public String hash(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw new IllegalArgumentException("refresh token must not be blank");
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
