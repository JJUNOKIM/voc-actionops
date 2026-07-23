package com.vocactionops.backend.issue.application;

public record IssuePriorityMetrics(
		long feedbackCount,
		long analyzedCount,
		long negativeCount,
		Double averageUrgency
) {

	public IssuePriorityMetrics {
		if (feedbackCount < 0
				|| analyzedCount < 0
				|| analyzedCount > feedbackCount
				|| negativeCount < 0
				|| negativeCount > analyzedCount
				|| (analyzedCount == 0 && averageUrgency != null)
				|| (analyzedCount > 0 && !validUrgency(averageUrgency))) {
			throw new IllegalArgumentException("issue priority metrics are invalid");
		}
	}

	private static boolean validUrgency(Double value) {
		return value != null && value >= 0 && value <= 1;
	}
}
