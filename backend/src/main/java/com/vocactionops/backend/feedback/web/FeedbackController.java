package com.vocactionops.backend.feedback.web;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.response.ApiResponse;
import com.vocactionops.backend.common.response.PageResponse;
import com.vocactionops.backend.config.OpenApiConfig;
import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.feedback.application.FeedbackQueryService;
import com.vocactionops.backend.feedback.application.FeedbackQueryService.FeedbackDetail;
import com.vocactionops.backend.feedback.application.FeedbackQueryService.FeedbackView;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feedbacks")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class FeedbackController {

	private final FeedbackQueryService feedbackQueryService;

	public FeedbackController(FeedbackQueryService feedbackQueryService) {
		this.feedbackQueryService = feedbackQueryService;
	}

	@GetMapping
	public ApiResponse<PageResponse<FeedbackView>> feedbacks(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@RequestParam(required = false) Long datasetId,
			@RequestParam(required = false) SourceType sourceType,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.success(feedbackQueryService.getFeedbacks(
				authenticatedUser,
				datasetId,
				sourceType,
				page,
				size
		));
	}

	@GetMapping("/{feedbackId}")
	public ApiResponse<FeedbackDetail> feedback(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long feedbackId
	) {
		return ApiResponse.success(feedbackQueryService.getFeedback(
				authenticatedUser,
				feedbackId
		));
	}
}
