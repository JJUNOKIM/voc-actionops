package com.vocactionops.backend.dashboard.application;

import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.domain.Priority;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TopIssueView(
		Long issueId,
		String title,
		String category,
		Priority priority,
		BigDecimal priorityScore,
		IssueStatus status,
		long feedbackCount,
		BigDecimal negativeFeedbackRate,
		long unresolvedActionCount,
		Long assigneeId,
		String assigneeName,
		LocalDateTime lastSeenAt
) {
}
