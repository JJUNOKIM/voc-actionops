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
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

	public record RoleRequest(@NotNull Role role) {
	}
}
