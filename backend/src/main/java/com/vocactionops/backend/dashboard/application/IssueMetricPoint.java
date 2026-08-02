package com.vocactionops.backend.dashboard.application;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IssueMetricPoint(
		LocalDate snapshotDate,
		long feedbackCount,
		long analyzedFeedbackCount,
		BigDecimal negativeFeedbackRate,
		BigDecimal averageSentimentScore,
		BigDecimal averageUrgencyScore,
		BigDecimal priorityScore,
		long unresolvedActionCount
) {
}
