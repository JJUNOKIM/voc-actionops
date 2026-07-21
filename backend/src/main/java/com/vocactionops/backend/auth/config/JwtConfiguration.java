package com.vocactionops.backend.auth.config;

import com.vocactionops.backend.user.domain.Role;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

import static com.vocactionops.backend.auth.token.JwtTokenProvider.EMAIL_CLAIM;
import static com.vocactionops.backend.auth.token.JwtTokenProvider.ORGANIZATION_ID_CLAIM;
import static com.vocactionops.backend.auth.token.JwtTokenProvider.ROLE_CLAIM;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {

	@Bean
	Clock systemClock() {
		return Clock.systemUTC();
	}

	@Bean
	SecretKey jwtSecretKey(JwtProperties properties) {
		return new SecretKeySpec(
				properties.secret().getBytes(StandardCharsets.UTF_8),
				"HmacSHA256"
		);
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey secretKey) {
		return NimbusJwtEncoder.withSecretKey(secretKey)
				.algorithm(MacAlgorithm.HS256)
				.build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey secretKey, JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(properties.issuer()),
				new JwtClaimValidator<Object>(JwtClaimNames.SUB, JwtConfiguration::isPositiveLong),
				new JwtClaimValidator<Object>(ORGANIZATION_ID_CLAIM, JwtConfiguration::isPositiveNumber),
				new JwtClaimValidator<Object>(EMAIL_CLAIM, JwtConfiguration::isNotBlank),
				new JwtClaimValidator<Object>(ROLE_CLAIM, JwtConfiguration::isKnownRole)
		);
		decoder.setJwtValidator(validator);
		return decoder;
	}

	private static boolean isPositiveLong(Object value) {
		if (!(value instanceof String text)) {
			return false;
		}
		try {
			return Long.parseLong(text) > 0;
		} catch (NumberFormatException exception) {
			return false;
		}
	}

	private static boolean isPositiveNumber(Object value) {
		return value instanceof Number number && number.longValue() > 0;
	}

	private static boolean isNotBlank(Object value) {
		return value instanceof String text && !text.isBlank();
	}

	private static boolean isKnownRole(Object value) {
		if (!(value instanceof String text)) {
			return false;
		}
		try {
			Role.valueOf(text);
			return true;
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}
}
