package com.vocactionops.backend.issue;

import com.vocactionops.backend.issue.application.IssueSimilarityCalculator;
import com.vocactionops.backend.issue.application.IssueSimilarityCalculator.Similarity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssueSimilarityCalculatorTests {

	private final IssueSimilarityCalculator calculator = new IssueSimilarityCalculator();

	@Test
	void normalizesCategoryCaseAndTextPunctuation() {
		Similarity similarity = calculator.calculate(
				" payment ",
				"Coupon payment FAILED!",
				"PAYMENT",
				"coupon-payment failed"
		);

		assertThat(similarity.categoryMatched()).isTrue();
		assertThat(similarity.categoryScore()).isEqualByComparingTo("0.3500");
		assertThat(similarity.characterSimilarity()).isEqualByComparingTo("1.0000");
		assertThat(similarity.tokenSimilarity()).isEqualByComparingTo("1.0000");
		assertThat(similarity.textSimilarity()).isEqualByComparingTo("1.0000");
		assertThat(similarity.score()).isEqualByComparingTo("1.0000");
	}

	@Test
	void ranksSimilarKoreanFeedbackAboveAnotherIssueInTheSameCategory() {
		Similarity similar = calculator.calculate(
				"PAYMENT",
				"쿠폰을 적용하면 결제가 완료되지 않습니다. 쿠폰 결제가 실패해요.",
				"PAYMENT",
				"쿠폰 결제 실패. 쿠폰 적용 후 결제 오류가 반복됩니다."
		);
		Similarity different = calculator.calculate(
				"PAYMENT",
				"쿠폰을 적용하면 결제가 완료되지 않습니다. 쿠폰 결제가 실패해요.",
				"PAYMENT",
				"결제 중 앱이 종료됩니다. 체크아웃 화면 충돌."
		);

		assertThat(similar.score()).isGreaterThan(different.score());
		assertThat(similar.score()).isGreaterThanOrEqualTo(new BigDecimal("0.4500"));
		assertThat(different.score()).isLessThan(new BigDecimal("0.4500"));
	}

	@Test
	void rejectsDifferentCategoriesBeforeTextComparison() {
		Similarity similarity = calculator.calculate(
				"PAYMENT",
				"Coupon payment failed",
				"DELIVERY",
				"Coupon payment failed"
		);

		assertThat(similarity.categoryMatched()).isFalse();
		assertThat(similarity.score()).isEqualByComparingTo("0");
	}

	@Test
	void rejectsBlankInputs() {
		assertThatThrownBy(() -> calculator.calculate("PAYMENT", " ", "PAYMENT", "payment"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> calculator.calculate(null, "payment", "PAYMENT", "payment"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
