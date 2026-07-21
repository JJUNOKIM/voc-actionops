package com.vocactionops.backend.auth.web;

import com.vocactionops.backend.auth.application.AuthService;
import com.vocactionops.backend.auth.application.AuthTokens;
import com.vocactionops.backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(TokenResponse.from(authService.login(
				request.email(),
				request.password()
		)));
	}

	@PostMapping("/refresh")
	public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return ApiResponse.success(TokenResponse.from(authService.refresh(request.refreshToken())));
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request.refreshToken());
		return ApiResponse.success(null, "로그아웃되었습니다.");
	}

	public record LoginRequest(
			@NotBlank(message = "email is required")
			@Email(message = "email format is invalid")
			String email,
			@NotBlank(message = "password is required")
			String password
	) {
		public LoginRequest {
			if (email != null) {
				email = email.trim();
			}
		}
	}

	public record RefreshTokenRequest(
			@NotBlank @Size(max = 512) String refreshToken
	) {
	}

	public record TokenResponse(
			String accessToken,
			String refreshToken,
			String tokenType,
			long expiresIn,
			long refreshTokenExpiresIn
	) {
		private static TokenResponse from(AuthTokens tokens) {
			return new TokenResponse(
					tokens.accessToken().value(),
					tokens.refreshToken(),
					"Bearer",
					tokens.accessToken().expiresIn(),
					tokens.refreshTokenExpiresIn()
			);
		}
	}
}
