package com.vocactionops.backend.dashboard.web;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.response.ApiResponse;
import com.vocactionops.backend.config.OpenApiConfig;
import com.vocactionops.backend.dashboard.application.CategoryBreakdownItem;
import com.vocactionops.backend.dashboard.application.DashboardInsightsService;
import com.vocactionops.backend.dashboard.application.DashboardSummary;
import com.vocactionops.backend.dashboard.application.DashboardSummaryService;
import com.vocactionops.backend.dashboard.application.TopIssueView;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class DashboardController {

	private final DashboardSummaryService dashboardSummaryService;
	private final DashboardInsightsService dashboardInsightsService;

	public DashboardController(
			DashboardSummaryService dashboardSummaryService,
			DashboardInsightsService dashboardInsightsService
	) {
		this.dashboardSummaryService = dashboardSummaryService;
		this.dashboardInsightsService = dashboardInsightsService;
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

	@GetMapping("/category-breakdown")
	public ApiResponse<List<CategoryBreakdownItem>> categoryBreakdown(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
	) {
		return ApiResponse.success(dashboardInsightsService.getCategoryBreakdown(
				authenticatedUser,
				from,
				to
		));
	}

	@GetMapping("/top-issues")
	public ApiResponse<List<TopIssueView>> topIssues(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@RequestParam(defaultValue = "10") int limit,
			@RequestParam(defaultValue = "priority_score") String sortBy
	) {
		return ApiResponse.success(dashboardInsightsService.getTopIssues(
				authenticatedUser,
				limit,
				sortBy
		));
	}
}
