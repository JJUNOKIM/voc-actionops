package com.vocactionops.backend.dashboard.web;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.response.ApiResponse;
import com.vocactionops.backend.config.OpenApiConfig;
import com.vocactionops.backend.dashboard.application.DashboardSummary;
import com.vocactionops.backend.dashboard.application.DashboardSummaryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class DashboardController {

	private final DashboardSummaryService dashboardSummaryService;

	public DashboardController(DashboardSummaryService dashboardSummaryService) {
		this.dashboardSummaryService = dashboardSummaryService;
	}

	@GetMapping("/summary")
	public ApiResponse<DashboardSummary> summary(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		return ApiResponse.success(dashboardSummaryService.getSummary(
				authenticatedUser,
				from,
				to
		));
	}
}
