package com.vocactionops.backend.issue.web;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.response.ApiResponse;
import com.vocactionops.backend.common.response.PageResponse;
import com.vocactionops.backend.config.OpenApiConfig;
import com.vocactionops.backend.issue.application.IssueCandidateService;
import com.vocactionops.backend.issue.application.IssueCandidateService.IssueCandidateView;
import com.vocactionops.backend.issue.application.IssueDraftService;
import com.vocactionops.backend.issue.application.IssueDraftService.IssueDraftView;
import com.vocactionops.backend.issue.application.IssueService;
import com.vocactionops.backend.issue.application.IssueService.FeedbackIssueView;
import com.vocactionops.backend.issue.application.IssueService.IssueDetail;
import com.vocactionops.backend.issue.application.IssueService.IssueFeedbackView;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class IssueFeedbackController {

	private final IssueService issueService;
	private final IssueCandidateService issueCandidateService;
	private final IssueDraftService issueDraftService;

	public IssueFeedbackController(
			IssueService issueService,
			IssueCandidateService issueCandidateService,
			IssueDraftService issueDraftService
	) {
		this.issueService = issueService;
		this.issueCandidateService = issueCandidateService;
		this.issueDraftService = issueDraftService;
	}

	@GetMapping("/feedbacks/{feedbackId}/issue-draft")
	public ApiResponse<IssueDraftView> issueDraft(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long feedbackId
	) {
		return ApiResponse.success(issueDraftService.getDraft(authenticatedUser, feedbackId));
	}

	@PostMapping("/feedbacks/{feedbackId}/issue-draft/confirm")
	public ApiResponse<IssueDetail> confirmIssueDraft(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long feedbackId,
			@Valid @RequestBody ConfirmIssueDraftRequest request
	) {
		return ApiResponse.success(issueDraftService.confirmDraft(
				authenticatedUser,
				feedbackId,
				request.analysisVersion(),
				request.title(),
				request.description(),
				request.assigneeId()
		), "신규 이슈가 생성되었습니다.");
	}

	@GetMapping("/feedbacks/{feedbackId}/issue-candidates")
	public ApiResponse<List<IssueCandidateView>> candidates(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long feedbackId,
			@RequestParam(defaultValue = "5") int limit
	) {
		return ApiResponse.success(issueCandidateService.getCandidates(
				authenticatedUser,
				feedbackId,
				limit
		));
	}

	@PostMapping("/feedbacks/{feedbackId}/issue-candidates/{issueId}/confirm")
	public ApiResponse<IssueFeedbackView> confirmCandidate(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long feedbackId,
			@PathVariable Long issueId,
			@Valid @RequestBody ConfirmCandidateRequest request
	) {
		return ApiResponse.success(issueCandidateService.confirmCandidate(
				authenticatedUser,
				feedbackId,
				issueId,
				request.representative()
		), "추천 이슈 연결이 확정되었습니다.");
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

	@PatchMapping("/feedbacks/{feedbackId}/issue-links/{issueId}")
	public ApiResponse<IssueFeedbackView> changeRepresentative(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long feedbackId,
			@PathVariable Long issueId,
			@Valid @RequestBody ChangeRepresentativeRequest request
	) {
		return ApiResponse.success(issueService.changeFeedbackRepresentative(
				authenticatedUser, feedbackId, issueId, request.representative()
		), "대표 피드백 지정이 변경되었습니다.");
	}

	@DeleteMapping("/feedbacks/{feedbackId}/issue-links/{issueId}")
	public ApiResponse<Void> unlinkFeedback(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long feedbackId,
			@PathVariable Long issueId
	) {
		issueService.unlinkFeedback(authenticatedUser, feedbackId, issueId);
		return ApiResponse.success(null, "이슈 연결이 해제되었습니다.");
	}

	@GetMapping("/feedbacks/{feedbackId}/issues")
	public ApiResponse<List<FeedbackIssueView>> feedbackIssues(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long feedbackId
	) {
		return ApiResponse.success(issueService.getFeedbackIssues(authenticatedUser, feedbackId));
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

	public record ConfirmCandidateRequest(boolean representative) {
	}

	public record ChangeRepresentativeRequest(@NotNull Boolean representative) {
	}

	public record ConfirmIssueDraftRequest(
			@NotNull @PositiveOrZero Long analysisVersion,
			@NotBlank @Size(max = 150) String title,
			@NotBlank @Size(max = 1000) String description,
			@Positive Long assigneeId
	) {
	}
}
