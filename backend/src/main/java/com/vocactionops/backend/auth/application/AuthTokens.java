package com.vocactionops.backend.auth.application;

import com.vocactionops.backend.auth.token.JwtTokenProvider.AccessToken;

public record AuthTokens(
		AccessToken accessToken,
		String refreshToken,
		long refreshTokenExpiresIn
) {
}
