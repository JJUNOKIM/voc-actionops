package com.vocactionops.backend.issue;

import com.vocactionops.backend.issue.application.IssueDraftGenerator;
import com.vocactionops.backend.issue.application.IssueDraftGenerator.Draft;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssueDraftGeneratorTests {

	private final IssueDraftGenerator generator = new IssueDraftGenerator();

	@Test
	void buildsEditableDraftFromSummaryAndOriginalContent() {
		Draft draft = generator.generate(
				"  Coupon   payment cannot be completed.  ",
				"  Coupon payment failed after applying a promotion.  "
		);

		assertThat(draft.title()).isEqualTo("Coupon payment cannot be completed");
		assertThat(draft.description())
				.isEqualTo("Coupon payment failed after applying a promotion.");
	}

	@Test
	void truncatesDraftWithinIssueColumnLimitsWithoutBreakingSurrogatePairs() {
		String summary = "A".repeat(149) + "😀" + "tail";
		String content = "나".repeat(1001);

		Draft draft = generator.generate(summary, content);

		assertThat(draft.title()).hasSize(149).doesNotContain("�");
		assertThat(draft.description()).hasSize(1000);
	}

	@Test
	void rejectsBlankSourceText() {
		assertThatThrownBy(() -> generator.generate(" ", "content"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> generator.generate("summary", null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
