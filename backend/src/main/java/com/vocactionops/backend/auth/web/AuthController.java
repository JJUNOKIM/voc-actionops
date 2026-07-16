package com.vocactionops.backend.auth.web;

import com.vocactionops.backend.auth.application.AuthService;
import com.vocactionops.backend.auth.token.JwtTokenProvider.AccessToken;
import com.vocactionops.backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		AccessToken accessToken = authService.login(request.email(), request.password());
		return ApiResponse.success(new LoginResponse(
				accessToken.value(),
				"Bearer",
				accessToken.expiresIn()
		));
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

	public record LoginResponse(
			String accessToken,
			String tokenType,
			long expiresIn
	) {
	}
}
