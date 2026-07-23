package com.vocactionops.backend.issue.application;

import com.vocactionops.backend.issue.domain.Priority;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Component
public class IssuePriorityCalculator {

	private static final BigDecimal MAX_FREQUENCY_COUNT = BigDecimal.valueOf(20);
	private static final BigDecimal FREQUENCY_WEIGHT = BigDecimal.valueOf(30);
	private static final BigDecimal NEGATIVE_RATIO_WEIGHT = BigDecimal.valueOf(35);
	private static final BigDecimal URGENCY_WEIGHT = BigDecimal.valueOf(35);
	private static final int RATIO_SCALE = 10;

	public Optional<PriorityResult> calculate(IssuePriorityMetrics metrics) {
		if (metrics.analyzedCount() == 0) {
			return Optional.empty();
		}

		BigDecimal frequencyRatio = ratio(
				metrics.feedbackCount(),
				MAX_FREQUENCY_COUNT.longValue()
		).min(BigDecimal.ONE);
		BigDecimal negativeRatio = ratio(metrics.negativeCount(), metrics.analyzedCount());
		BigDecimal urgency = BigDecimal.valueOf(metrics.averageUrgency());
		BigDecimal score = frequencyRatio.multiply(FREQUENCY_WEIGHT)
				.add(negativeRatio.multiply(NEGATIVE_RATIO_WEIGHT))
				.add(urgency.multiply(URGENCY_WEIGHT))
				.setScale(2, RoundingMode.HALF_UP);

		return Optional.of(new PriorityResult(score, Priority.fromScore(score)));
	}

	private static BigDecimal ratio(long numerator, long denominator) {
		return BigDecimal.valueOf(numerator)
				.divide(BigDecimal.valueOf(denominator), RATIO_SCALE, RoundingMode.HALF_UP);
	}

	public record PriorityResult(BigDecimal score, Priority priority) {
	}
}
