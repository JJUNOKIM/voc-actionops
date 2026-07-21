package com.vocactionops.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties("security.jwt")
public record JwtProperties(
		String issuer,
		String secret,
		Duration accessTokenExpiration,
		Duration refreshTokenExpiration
) {

	private static final int MINIMUM_SECRET_BYTES = 32;

	public JwtProperties {
		if (issuer == null || issuer.isBlank()) {
			throw new IllegalArgumentException("security.jwt.issuer must not be blank");
		}
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
			throw new IllegalArgumentException("security.jwt.secret must be at least 32 bytes");
		}
		if (accessTokenExpiration == null || accessTokenExpiration.isZero()
				|| accessTokenExpiration.isNegative()) {
			throw new IllegalArgumentException("security.jwt.access-token-expiration must be positive");
		}
		if (refreshTokenExpiration == null || refreshTokenExpiration.isZero()
				|| refreshTokenExpiration.isNegative()) {
			throw new IllegalArgumentException("security.jwt.refresh-token-expiration must be positive");
		}
	}
}
