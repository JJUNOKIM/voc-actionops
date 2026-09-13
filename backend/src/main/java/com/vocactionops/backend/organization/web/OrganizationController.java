package com.vocactionops.backend.organization.web;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.response.ApiResponse;
import com.vocactionops.backend.config.OpenApiConfig;
import com.vocactionops.backend.organization.application.OrganizationService;
import com.vocactionops.backend.organization.application.OrganizationService.OrganizationView;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/me")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class OrganizationController {

	private final OrganizationService organizationService;

	public OrganizationController(OrganizationService organizationService) {
		this.organizationService = organizationService;
	}

	@PatchMapping
	public ApiResponse<OrganizationView> changeName(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@Valid @RequestBody OrganizationNameRequest request
	) {
		return ApiResponse.success(
				organizationService.changeName(authenticatedUser, request.name()),
				"조직 정보가 변경되었습니다."
		);
	}

	public record OrganizationNameRequest(@NotBlank @Size(max = 100) String name) {
	}
}
