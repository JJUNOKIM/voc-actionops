package com.vocactionops.backend.dashboard.application;

import com.vocactionops.backend.issue.domain.IssueStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record IssueTrendView(
		Long issueId,
		String title,
		String category,
		IssueStatus status,
		LocalDateTime resolvedAt,
		LocalDate from,
		LocalDate to,
		BigDecimal feedbackGrowthRate,
		List<IssueMetricPoint> points
) {
}
