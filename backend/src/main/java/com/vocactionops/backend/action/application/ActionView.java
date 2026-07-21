package com.vocactionops.backend.action.application;

import com.vocactionops.backend.action.domain.Action;
import com.vocactionops.backend.action.domain.ActionStatus;
import com.vocactionops.backend.user.domain.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ActionView(
		Long id,
		Long issueId,
		String title,
		String description,
		ActionStatus status,
		Long assigneeId,
		String assigneeName,
		LocalDate dueDate,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDateTime completedAt
) {

	public static ActionView from(Action action) {
		User assignee = action.getAssignee();
		return new ActionView(
				action.getId(),
				action.getIssue().getId(),
				action.getTitle(),
				action.getDescription(),
				action.getStatus(),
				assignee == null ? null : assignee.getId(),
				assignee == null ? null : assignee.getName(),
				action.getDueDate(),
				action.getCreatedAt(),
				action.getUpdatedAt(),
				action.getCompletedAt()
		);
	}
}
