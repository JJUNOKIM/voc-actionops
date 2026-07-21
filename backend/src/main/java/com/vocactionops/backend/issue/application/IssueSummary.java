package com.vocactionops.backend.issue.application;

import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.domain.Priority;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IssueSummary(
		Long id,
		String title,
		String category,
		Priority priority,
		BigDecimal priorityScore,
		IssueStatus status,
		Long assigneeId,
		String assigneeName,
		long feedbackCount,
		long negativeCount,
		LocalDateTime firstSeenAt,
		LocalDateTime lastSeenAt,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
}
