package com.vocactionops.backend.auth.application;

import com.vocactionops.backend.auth.config.JwtProperties;
import com.vocactionops.backend.auth.domain.RefreshToken;
import com.vocactionops.backend.auth.exception.RefreshTokenReuseException;
import com.vocactionops.backend.auth.repository.RefreshTokenRepository;
import com.vocactionops.backend.auth.token.JwtTokenProvider;
import com.vocactionops.backend.auth.token.JwtTokenProvider.AccessToken;
import com.vocactionops.backend.auth.token.RefreshTokenCodec;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final RefreshTokenCodec refreshTokenCodec;
	private final JwtTokenProvider jwtTokenProvider;
	private final Duration refreshTokenExpiration;
	private final Clock clock;

	public RefreshTokenService(
			RefreshTokenRepository refreshTokenRepository,
			RefreshTokenCodec refreshTokenCodec,
			JwtTokenProvider jwtTokenProvider,
			JwtProperties jwtProperties,
			Clock clock
	) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.refreshTokenCodec = refreshTokenCodec;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenExpiration = jwtProperties.refreshTokenExpiration();
		this.clock = clock;
	}

	@Transactional
	public IssuedRefreshToken issue(User user) {
		return issue(user, UUID.randomUUID().toString(), clock.instant());
	}

	@Transactional(noRollbackFor = RefreshTokenReuseException.class)
	public AuthTokens rotate(String rawToken) {
		String tokenHash = hash(rawToken);
		RefreshToken currentToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
				.orElseThrow(RefreshTokenService::invalidToken);
		Instant now = clock.instant();

		if (currentToken.isUsed()) {
			refreshTokenRepository.revokeFamily(currentToken.getFamilyId(), now);
			throw new RefreshTokenReuseException();
		}
		if (currentToken.isRevoked() || currentToken.isExpired(now)) {
			throw invalidToken();
		}

		IssuedRefreshToken replacement = issue(
				currentToken.getUser(),
				currentToken.getFamilyId(),
				now
		);
		try {
			currentToken.rotateTo(replacement.entity(), now);
		} catch (IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
		} catch (IllegalStateException exception) {
			throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		AccessToken accessToken = jwtTokenProvider.issue(currentToken.getUser());
		return new AuthTokens(
				accessToken,
				replacement.value(),
				replacement.expiresIn()
		);
	}

	@Transactional
	public void logout(String rawToken) {
		String tokenHash = hash(rawToken);
		refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
				.ifPresent(token -> refreshTokenRepository.revokeFamily(
						token.getFamilyId(),
						clock.instant()
				));
	}

	private IssuedRefreshToken issue(User user, String familyId, Instant issuedAt) {
		String rawToken = refreshTokenCodec.generate();
		RefreshToken entity = refreshTokenRepository.save(new RefreshToken(
				user,
				refreshTokenCodec.hash(rawToken),
				familyId,
				issuedAt,
				issuedAt.plus(refreshTokenExpiration)
		));
		return new IssuedRefreshToken(
				rawToken,
				refreshTokenExpiration.toSeconds(),
				entity
		);
	}

	private String hash(String rawToken) {
		try {
			return refreshTokenCodec.hash(rawToken);
		} catch (IllegalArgumentException exception) {
			throw invalidToken();
		}
	}

	private static CustomException invalidToken() {
		return new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
	}

	public record IssuedRefreshToken(
			String value,
			long expiresIn,
			RefreshToken entity
	) {
	}
}
