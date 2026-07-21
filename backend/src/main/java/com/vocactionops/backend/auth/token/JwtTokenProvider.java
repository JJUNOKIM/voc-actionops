package com.vocactionops.backend.auth.token;

import com.vocactionops.backend.auth.config.JwtProperties;
import com.vocactionops.backend.user.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class JwtTokenProvider {

	public static final String ORGANIZATION_ID_CLAIM = "organizationId";
	public static final String EMAIL_CLAIM = "email";
	public static final String ROLE_CLAIM = "role";

	private final JwtEncoder jwtEncoder;
	private final JwtProperties properties;

	public JwtTokenProvider(JwtEncoder jwtEncoder, JwtProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	public AccessToken issue(User user) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plus(properties.accessTokenExpiration());

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.id(UUID.randomUUID().toString())
				.issuer(properties.issuer())
				.subject(user.getId().toString())
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim(ORGANIZATION_ID_CLAIM, user.getOrganization().getId())
				.claim(EMAIL_CLAIM, user.getEmail())
				.claim(ROLE_CLAIM, user.getRole().name())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
				.type("JWT")
				.build();

		String tokenValue = jwtEncoder
				.encode(JwtEncoderParameters.from(header, claims))
				.getTokenValue();

		return new AccessToken(tokenValue, properties.accessTokenExpiration().toSeconds());
	}

	public record AccessToken(
			String value,
			long expiresIn
	) {
	}
}
