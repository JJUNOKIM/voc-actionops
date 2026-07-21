package com.vocactionops.backend.auth.application;

import com.vocactionops.backend.auth.token.JwtTokenProvider.AccessToken;
import com.vocactionops.backend.auth.token.JwtTokenProvider;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenService refreshTokenService;
	private final String dummyPasswordHash;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider,
			RefreshTokenService refreshTokenService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenService = refreshTokenService;
		this.dummyPasswordHash = passwordEncoder.encode("unused-password");
	}

	@Transactional
	public AuthTokens login(String email, String password) {
		User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
		String passwordHash = user == null ? dummyPasswordHash : user.getPasswordHash();

		if (!passwordEncoder.matches(password, passwordHash) || user == null) {
			throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
		}

		AccessToken accessToken = jwtTokenProvider.issue(user);
		RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issue(user);
		return new AuthTokens(
				accessToken,
				refreshToken.value(),
				refreshToken.expiresIn()
		);
	}

	public AuthTokens refresh(String refreshToken) {
		return refreshTokenService.rotate(refreshToken);
	}

	public void logout(String refreshToken) {
		refreshTokenService.logout(refreshToken);
	}
}
