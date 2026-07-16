package com.vocactionops.backend.dataset;

import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dataset.application.CsvFeedbackParser;
import com.vocactionops.backend.dataset.application.CsvFeedbackParser.CsvParseResult;
import com.vocactionops.backend.dataset.config.DatasetUploadProperties;
import com.vocactionops.backend.dataset.domain.DatasetValidationErrorCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvFeedbackParserTests {

	private final CsvFeedbackParser parser = parserWithMaxRecords(100);

	@Test
	void parsesBomQuotedContentAndSupportedDateFormats() {
		String csv = """
				\uFEFFreview_id,review_text,score,created_date
				review-001,"Works, great",4.5,2026-07-01
				review-002,Stable,5,2026-07-01 12:30:00
				""";

		CsvParseResult result = parser.parse(bytes(csv), mapping());

		assertThat(result.totalCount()).isEqualTo(2);
		assertThat(result.invalidCount()).isZero();
		assertThat(result.feedbacks()).hasSize(2);
		assertThat(result.feedbacks().get(0).content()).isEqualTo("Works, great");
		assertThat(result.feedbacks().get(0).feedbackCreatedAt())
				.isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
		assertThat(result.feedbacks().get(1).feedbackCreatedAt())
				.isEqualTo(LocalDateTime.of(2026, 7, 1, 12, 30));
	}

	@Test
	void recordsRowErrorsAndKeepsOnlyValidFeedbacks() {
		String csv = """
				review_id,review_text,score,created_date
				review-001,Valid feedback,4.0,2026-07-01
				review-002, ,3.0,2026-07-01
				review-003,Invalid rating,5.5,2026-07-01
				review-004,Invalid date,2.0,July first
				review-001,Duplicate id,1.0,2026-07-02
				review-005
				""";

		CsvParseResult result = parser.parse(bytes(csv), mapping());

		assertThat(result.totalCount()).isEqualTo(6);
		assertThat(result.feedbacks()).singleElement()
				.extracting(feedback -> feedback.externalId())
				.isEqualTo("review-001");
		assertThat(result.invalidCount()).isEqualTo(5);
		assertThat(result.validationErrors())
				.extracting(error -> error.errorCode())
				.containsExactly(
						DatasetValidationErrorCode.EMPTY_CONTENT,
						DatasetValidationErrorCode.INVALID_RATING_RANGE,
						DatasetValidationErrorCode.INVALID_DATE_FORMAT,
						DatasetValidationErrorCode.DUPLICATED_EXTERNAL_ID,
						DatasetValidationErrorCode.MISSING_REQUIRED_FIELD
				);
	}

	@Test
	void rejectsInvalidMappingBeforeCreatingRowResults() {
		String csv = "review_text,score\nGood,5\n";

		assertThatThrownBy(() -> parser.parse(
				bytes(csv),
				Map.of("missing_header", "content")
		))
				.isInstanceOf(CustomException.class)
				.extracting(exception -> ((CustomException) exception).errorCode())
				.isEqualTo(ErrorCode.CSV_VALIDATION_FAILED);

		assertThatThrownBy(() -> parser.parse(
				bytes(csv),
				Map.of("review_text", "unknown_field")
		))
				.isInstanceOf(CustomException.class)
				.extracting(exception -> ((CustomException) exception).errorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);
	}

	@Test
	void rejectsCsvThatExceedsConfiguredRecordLimit() {
		CsvFeedbackParser limitedParser = parserWithMaxRecords(1);
		String csv = "review_text\nFirst\nSecond\n";

		assertThatThrownBy(() -> limitedParser.parse(
				bytes(csv),
				Map.of("review_text", "content")
		))
				.isInstanceOf(CustomException.class)
				.extracting(exception -> ((CustomException) exception).errorCode())
				.isEqualTo(ErrorCode.CSV_VALIDATION_FAILED);
	}

	@Test
	void rejectsMalformedUtf8Input() {
		byte[] malformedUtf8 = {(byte) 0xC3, (byte) 0x28};

		assertThatThrownBy(() -> parser.parse(
				malformedUtf8,
				Map.of("review_text", "content")
		))
				.isInstanceOf(CustomException.class)
				.extracting(exception -> ((CustomException) exception).errorCode())
				.isEqualTo(ErrorCode.CSV_VALIDATION_FAILED);
	}

	private CsvFeedbackParser parserWithMaxRecords(int maxRecords) {
		return new CsvFeedbackParser(new DatasetUploadProperties(
				Path.of("build/test-uploads"),
				1024 * 1024,
				maxRecords
		));
	}

	private Map<String, String> mapping() {
		return Map.of(
				"review_id", "external_id",
				"review_text", "content",
				"score", "rating",
				"created_date", "feedback_created_at"
		);
	}

	private byte[] bytes(String csv) {
		return csv.getBytes(StandardCharsets.UTF_8);
	}
}
