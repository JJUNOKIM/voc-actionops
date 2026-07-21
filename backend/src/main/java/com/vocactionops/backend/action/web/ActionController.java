package com.vocactionops.backend.action.web;

import com.vocactionops.backend.action.application.ActionService;
import com.vocactionops.backend.action.application.ActionView;
import com.vocactionops.backend.action.domain.ActionStatus;
import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.response.ApiResponse;
import com.vocactionops.backend.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class ActionController {

	private final ActionService actionService;

	public ActionController(ActionService actionService) {
		this.actionService = actionService;
	}

	@PostMapping("/issues/{issueId}/actions")
	public ApiResponse<ActionView> create(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long issueId,
			@Valid @RequestBody CreateActionRequest request
	) {
		return ApiResponse.success(actionService.createAction(
				authenticatedUser,
				issueId,
				request.title(),
				request.description(),
				request.assigneeId(),
				request.dueDate()
		), "액션이 생성되었습니다.");
	}

	@PatchMapping("/actions/{actionId}/status")
	public ApiResponse<ActionView> changeStatus(
			@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
			@PathVariable Long actionId,
			@Valid @RequestBody StatusRequest request
	) {
		return ApiResponse.success(
				actionService.changeStatus(authenticatedUser, actionId, request.status()),
				"액션 상태가 변경되었습니다."
		);
	}

	public record CreateActionRequest(
			@NotBlank @Size(max = 150) String title,
			@Size(max = 1000) String description,
			@Positive Long assigneeId,
			LocalDate dueDate
	) {
	}

	public record StatusRequest(@NotNull ActionStatus status) {
	}
}
