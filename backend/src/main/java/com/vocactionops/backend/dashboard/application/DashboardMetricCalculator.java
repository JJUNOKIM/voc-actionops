package com.vocactionops.backend.dashboard.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class DashboardMetricCalculator {

	private static final int METRIC_SCALE = 2;

	private DashboardMetricCalculator() {
	}

	static BigDecimal percentage(long numerator, long denominator) {
		if (denominator == 0) {
			return BigDecimal.ZERO.setScale(METRIC_SCALE);
		}
		return BigDecimal.valueOf(numerator)
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(denominator), METRIC_SCALE, RoundingMode.HALF_UP);
	}

	static BigDecimal normalize(BigDecimal value) {
		return value == null ? null : value.setScale(METRIC_SCALE, RoundingMode.HALF_UP);
	}

	static BigDecimal growthRate(long latest, long previous) {
		if (previous == 0) {
			return null;
		}
		return BigDecimal.valueOf(latest - previous)
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(previous), METRIC_SCALE, RoundingMode.HALF_UP);
	}
}
