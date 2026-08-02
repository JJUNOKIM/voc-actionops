package com.vocactionops.backend.dashboard.application;

import java.math.BigDecimal;

public record CategoryBreakdownItem(
		String category,
		long issueCount,
		long feedbackCount,
		BigDecimal negativeFeedbackRate
) {
}
