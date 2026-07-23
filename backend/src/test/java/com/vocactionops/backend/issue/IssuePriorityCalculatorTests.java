package com.vocactionops.backend.issue;

import com.vocactionops.backend.issue.application.IssuePriorityCalculator;
import com.vocactionops.backend.issue.application.IssuePriorityMetrics;
import com.vocactionops.backend.issue.domain.Priority;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssuePriorityCalculatorTests {

	private final IssuePriorityCalculator calculator = new IssuePriorityCalculator();

	@Test
	void calculatesWeightedScoreAndPriority() {
		IssuePriorityCalculator.PriorityResult result = calculator.calculate(
				new IssuePriorityMetrics(2, 1, 1, 0.9)
		).orElseThrow();

		assertThat(result.score()).isEqualByComparingTo("69.50");
		assertThat(result.priority()).isEqualTo(Priority.P1);
	}

	@Test
	void capsFrequencyAndScoreAtMaximum() {
		IssuePriorityCalculator.PriorityResult result = calculator.calculate(
				new IssuePriorityMetrics(100, 20, 20, 1.0)
		).orElseThrow();

		assertThat(result.score()).isEqualByComparingTo("100.00");
		assertThat(result.priority()).isEqualTo(Priority.P0);
	}

	@Test
	void keepsManualPriorityWhenNoSuccessfulAnalysisExists() {
		assertThat(calculator.calculate(new IssuePriorityMetrics(3, 0, 0, null)))
				.isEmpty();
	}

	@Test
	void mapsScoreBoundariesAndRejectsInvalidMetrics() {
		assertThat(Priority.fromScore(new BigDecimal("80.00"))).isEqualTo(Priority.P0);
		assertThat(Priority.fromScore(new BigDecimal("60.00"))).isEqualTo(Priority.P1);
		assertThat(Priority.fromScore(new BigDecimal("40.00"))).isEqualTo(Priority.P2);
		assertThat(Priority.fromScore(new BigDecimal("39.99"))).isEqualTo(Priority.P3);

		assertThatThrownBy(() -> new IssuePriorityMetrics(1, 2, 0, 0.5))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
