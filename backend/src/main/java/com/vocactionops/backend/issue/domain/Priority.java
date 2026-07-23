package com.vocactionops.backend.issue.domain;

import java.math.BigDecimal;

public enum Priority {
	P0,
	P1,
	P2,
	P3;

	private static final BigDecimal P0_THRESHOLD = BigDecimal.valueOf(80);
	private static final BigDecimal P1_THRESHOLD = BigDecimal.valueOf(60);
	private static final BigDecimal P2_THRESHOLD = BigDecimal.valueOf(40);

	public static Priority fromScore(BigDecimal score) {
		if (score == null || score.compareTo(BigDecimal.ZERO) < 0
				|| score.compareTo(BigDecimal.valueOf(100)) > 0) {
			throw new IllegalArgumentException("priority score is invalid");
		}
		if (score.compareTo(P0_THRESHOLD) >= 0) {
			return P0;
		}
		if (score.compareTo(P1_THRESHOLD) >= 0) {
			return P1;
		}
		if (score.compareTo(P2_THRESHOLD) >= 0) {
			return P2;
		}
		return P3;
	}
}
