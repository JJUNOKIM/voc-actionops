package com.vocactionops.backend.dashboard.application;

import java.math.BigDecimal;

public record DashboardSummary(
		long totalFeedbackCount,
		BigDecimal negativeFeedbackRate,
		long newIssueCount,
		long p0IssueCount,
		long p1IssueCount,
		long unresolvedIssueCount,
		BigDecimal averageResolutionHours
) {
}
