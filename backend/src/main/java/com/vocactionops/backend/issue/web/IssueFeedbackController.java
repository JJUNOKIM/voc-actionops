package com.vocactionops.backend.issue.web;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.response.ApiResponse;
import com.vocactionops.backend.common.response.PageResponse;
import com.vocactionops.backend.config.OpenApiConfig;
import com.vocactionops.backend.issue.application.IssueService;
import com.vocactionops.backend.issue.application.IssueService.IssueFeedbackView;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class IssueFeedbackController {

	private final IssueService issueService;

	public IssueFeedbackController(IssueService issueService) {
		this.issueService = issueService;
	}

	@PostMapping("/feedbacks/{feedbackId}/issue-links")
	public ApiResponse<IssueFeedbackView> linkFeedback(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long feedbackId,
			@Valid @RequestBody LinkFeedbackRequest request
	) {
		return ApiResponse.success(issueService.linkFeedback(
				authenticatedUser,
				feedbackId,
				request.issueId(),
				request.representative()
		), "피드백이 이슈에 연결되었습니다.");
	}

	@GetMapping("/issues/{issueId}/feedbacks")
	public ApiResponse<PageResponse<IssueFeedbackView>> feedbacks(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long issueId,
			@RequestParam(defaultValue = "false") boolean representativeOnly,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.success(issueService.getIssueFeedbacks(
				authenticatedUser,
				issueId,
				representativeOnly,
				page,
				size
		));
	}

	public record LinkFeedbackRequest(
			@NotNull @Positive Long issueId,
			boolean representative
	) {
	}
}
