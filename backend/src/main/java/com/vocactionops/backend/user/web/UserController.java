package com.vocactionops.backend.user.web;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.response.ApiResponse;
import com.vocactionops.backend.config.OpenApiConfig;
import com.vocactionops.backend.user.application.UserQueryService;
import com.vocactionops.backend.user.application.UserQueryService.OrganizationUser;
import com.vocactionops.backend.user.application.UserQueryService.UserProfile;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class UserController {

	private final UserQueryService userQueryService;

	public UserController(UserQueryService userQueryService) {
		this.userQueryService = userQueryService;
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
}
