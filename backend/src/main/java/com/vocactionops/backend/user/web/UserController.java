package com.vocactionops.backend.user.web;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.response.ApiResponse;
import com.vocactionops.backend.config.OpenApiConfig;
import com.vocactionops.backend.user.application.UserCommandService;
import com.vocactionops.backend.user.application.UserQueryService;
import com.vocactionops.backend.user.application.UserQueryService.OrganizationUser;
import com.vocactionops.backend.user.application.UserQueryService.UserProfile;
import com.vocactionops.backend.user.domain.Role;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class UserController {

	private final UserQueryService userQueryService;
	private final UserCommandService userCommandService;

	public UserController(
			UserQueryService userQueryService,
			UserCommandService userCommandService
	) {
		this.userQueryService = userQueryService;
		this.userCommandService = userCommandService;
	}

	@GetMapping("/me")
	public ApiResponse<UserProfile> me(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser
	) {
		return ApiResponse.success(userQueryService.getCurrentUser(authenticatedUser));
	}

	@GetMapping
	public ApiResponse<List<OrganizationUser>> organizationUsers(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser
	) {
		return ApiResponse.success(userQueryService.getOrganizationUsers(authenticatedUser));
	}

	@PostMapping
	public ApiResponse<OrganizationUser> create(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@Valid @RequestBody CreateUserRequest request
	) {
		return ApiResponse.success(
				userCommandService.createUser(
						authenticatedUser,
						request.email(),
						request.password(),
						request.name(),
						request.role()
				),
				"사용자가 생성되었습니다."
		);
	}

	@PatchMapping("/{userId}/role")
	public ApiResponse<OrganizationUser> changeRole(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long userId,
			@Valid @RequestBody RoleRequest request
	) {
		return ApiResponse.success(
				userCommandService.changeRole(authenticatedUser, userId, request.role()),
				"사용자 역할이 변경되었습니다."
		);
	}

	@PatchMapping("/me/password")
	public ApiResponse<Void> changePassword(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@Valid @RequestBody PasswordChangeRequest request
	) {
		userCommandService.changePassword(
				authenticatedUser,
				request.currentPassword(),
				request.newPassword()
		);
		return ApiResponse.success(null, "비밀번호가 변경되었습니다.");
	}

	public record RoleRequest(@NotNull Role role) {
	}

	public record PasswordChangeRequest(
			@NotBlank @Size(max = 72) String currentPassword,
			@NotBlank @Size(min = 8, max = 72) String newPassword
	) {
	}

	public record CreateUserRequest(
			@NotBlank @Email @Size(max = 255) String email,
			@NotBlank @Size(min = 8, max = 72) String password,
			@NotBlank @Size(max = 100) String name,
			@NotNull Role role
	) {
		public CreateUserRequest {
			if (email != null) {
				email = email.trim();
			}
			if (name != null) {
				name = name.trim();
			}
		}
	}
}
