package com.vocactionops.backend.auth.security;

import com.vocactionops.backend.user.domain.Role;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.vocactionops.backend.auth.token.JwtTokenProvider.EMAIL_CLAIM;
import static com.vocactionops.backend.auth.token.JwtTokenProvider.ORGANIZATION_ID_CLAIM;
import static com.vocactionops.backend.auth.token.JwtTokenProvider.ROLE_CLAIM;

@Component
public class JwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {

	@Override
	public JwtAuthenticationToken convert(Jwt jwt) {
		Role role = Role.valueOf(jwt.getClaimAsString(ROLE_CLAIM));
		Number organizationId = jwt.getClaim(ORGANIZATION_ID_CLAIM);
		AuthenticatedUser principal = new AuthenticatedUser(
				Long.valueOf(jwt.getSubject()),
				organizationId.longValue(),
				jwt.getClaimAsString(EMAIL_CLAIM),
				role
		);

		return new PrincipalJwtAuthenticationToken(
				jwt,
				principal,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
		);
	}

	private static final class PrincipalJwtAuthenticationToken extends JwtAuthenticationToken {

		private final AuthenticatedUser principal;

		private PrincipalJwtAuthenticationToken(
				Jwt jwt,
				AuthenticatedUser principal,
				List<SimpleGrantedAuthority> authorities
		) {
			super(jwt, authorities, principal.email());
			this.principal = principal;
		}

		@Override
		public Object getPrincipal() {
			return principal;
		}
	}
}
