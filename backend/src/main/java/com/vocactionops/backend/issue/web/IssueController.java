package com.vocactionops.backend.issue.web;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.response.ApiResponse;
import com.vocactionops.backend.common.response.PageResponse;
import com.vocactionops.backend.config.OpenApiConfig;
import com.vocactionops.backend.issue.application.IssueService;
import com.vocactionops.backend.issue.application.IssueService.IssueDetail;
import com.vocactionops.backend.issue.application.IssueSummary;
import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.domain.Priority;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/issues")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class IssueController {

	private final IssueService issueService;

	public IssueController(IssueService issueService) {
		this.issueService = issueService;
	}

	@PostMapping
	public ApiResponse<IssueDetail> create(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@Valid @RequestBody CreateIssueRequest request
	) {
		return ApiResponse.success(issueService.createIssue(
				authenticatedUser,
				request.title(),
				request.description(),
				request.category(),
				request.priority(),
				request.assigneeId()
		), "이슈가 생성되었습니다.");
	}

	@GetMapping
	public ApiResponse<PageResponse<IssueSummary>> issues(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@RequestParam(required = false) IssueStatus status,
			@RequestParam(required = false) Priority priority,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) Long assigneeId,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.success(issueService.getIssues(
				authenticatedUser,
				status,
				priority,
				category,
				assigneeId,
				keyword,
				from,
				to,
				page,
				size
		));
	}

	@GetMapping("/{issueId}")
	public ApiResponse<IssueDetail> issue(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long issueId
	) {
		return ApiResponse.success(issueService.getIssue(authenticatedUser, issueId));
	}

	@PatchMapping("/{issueId}/status")
	public ApiResponse<IssueDetail> changeStatus(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long issueId,
			@Valid @RequestBody StatusRequest request
	) {
		return ApiResponse.success(
				issueService.changeStatus(authenticatedUser, issueId, request.status()),
				"이슈 상태가 변경되었습니다."
		);
	}

	@PatchMapping("/{issueId}/assignee")
	public ApiResponse<IssueDetail> assign(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long issueId,
			@Valid @RequestBody AssigneeRequest request
	) {
		return ApiResponse.success(
				issueService.assignIssue(authenticatedUser, issueId, request.assigneeId()),
				"이슈 담당자가 변경되었습니다."
		);
	}

	public record CreateIssueRequest(
			@NotBlank @Size(max = 150) String title,
			@NotBlank @Size(max = 1000) String description,
			@NotBlank @Size(max = 100) String category,
			@NotNull Priority priority,
			@Positive Long assigneeId
	) {
	}

	public record StatusRequest(@NotNull IssueStatus status) {
	}

	public record AssigneeRequest(@NotNull @Positive Long assigneeId) {
	}
}
