package com.vocactionops.backend.dataset.application;

import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dataset.config.DatasetUploadProperties;
import com.vocactionops.backend.dataset.domain.DatasetValidationErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CsvFeedbackParser {

	private static final BigDecimal MIN_RATING = BigDecimal.ZERO;
	private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);
	private static final DateTimeFormatter SPACE_SEPARATED_DATE_TIME =
			DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

	private final int maxRecords;

	public CsvFeedbackParser(DatasetUploadProperties properties) {
		this.maxRecords = properties.maxRecords();
	}

	public CsvParseResult parse(byte[] content, Map<String, String> requestedColumnMapping) {
		CSVFormat format = CSVFormat.DEFAULT.builder()
				.setHeader()
				.setSkipHeaderRecord(true)
				.setIgnoreEmptyLines(true)
				.setAllowMissingColumnNames(false)
				.setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
				.get();

		try (Reader reader = new InputStreamReader(
				new ByteArrayInputStream(content),
				StandardCharsets.UTF_8.newDecoder()
						.onMalformedInput(CodingErrorAction.REPORT)
						.onUnmappableCharacter(CodingErrorAction.REPORT)
		); CSVParser parser = format.parse(reader)) {
			ValidatedColumnMapping columnMapping = validateColumnMapping(
					requestedColumnMapping,
					parser.getHeaderNames()
			);
			return parseRecords(parser, columnMapping);
		} catch (CustomException exception) {
			throw exception;
		} catch (IOException | UncheckedIOException | IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.CSV_VALIDATION_FAILED);
		}
	}

	private CsvParseResult parseRecords(CSVParser parser, ValidatedColumnMapping columnMapping) {
		List<ParsedFeedback> feedbacks = new ArrayList<>();
		List<RowValidationError> validationErrors = new ArrayList<>();
		Set<String> externalIds = new HashSet<>();
		int totalCount = 0;
		int invalidCount = 0;

		for (CSVRecord record : parser) {
			totalCount++;
			if (totalCount > maxRecords) {
				throw new CustomException(ErrorCode.CSV_VALIDATION_FAILED);
			}

			int rowNumber = Math.toIntExact(record.getRecordNumber() + 1);
			Map<String, String> rawRow = rawRow(record, parser.getHeaderNames());
			List<RowValidationError> rowErrors = new ArrayList<>();
			MappedValue contentValue = mappedValue(record, columnMapping, FeedbackCsvField.CONTENT);
			String feedbackContent = normalizeNullable(contentValue.value());

			if (!contentValue.present()) {
				rowErrors.add(error(
						rowNumber,
						FeedbackCsvField.CONTENT,
						DatasetValidationErrorCode.MISSING_REQUIRED_FIELD,
						"content column is missing from the row",
						rawRow
				));
			} else if (feedbackContent == null) {
				rowErrors.add(error(
						rowNumber,
						FeedbackCsvField.CONTENT,
						DatasetValidationErrorCode.EMPTY_CONTENT,
						"content must not be blank",
						rawRow
				));
			}

			BigDecimal rating = parseRating(
					mappedValue(record, columnMapping, FeedbackCsvField.RATING),
					rowNumber,
					rawRow,
					rowErrors
			);
			LocalDateTime feedbackCreatedAt = parseFeedbackCreatedAt(
					mappedValue(record, columnMapping, FeedbackCsvField.FEEDBACK_CREATED_AT),
					rowNumber,
					rawRow,
					rowErrors
			);
			String externalId = mappedText(record, columnMapping, FeedbackCsvField.EXTERNAL_ID);
			if (externalId != null && externalIds.contains(externalId)) {
				rowErrors.add(error(
						rowNumber,
						FeedbackCsvField.EXTERNAL_ID,
						DatasetValidationErrorCode.DUPLICATED_EXTERNAL_ID,
						"external_id is duplicated in this dataset",
						rawRow
				));
			}

			if (rowErrors.isEmpty()) {
				if (externalId != null) {
					externalIds.add(externalId);
				}
				feedbacks.add(new ParsedFeedback(
						externalId,
						mappedText(record, columnMapping, FeedbackCsvField.CUSTOMER_SEGMENT),
						mappedText(record, columnMapping, FeedbackCsvField.PRODUCT_NAME),
						rating,
						feedbackContent,
						mappedText(record, columnMapping, FeedbackCsvField.LANGUAGE),
						feedbackCreatedAt
				));
			} else {
				invalidCount++;
				validationErrors.addAll(rowErrors);
			}
		}

		if (totalCount == 0) {
			throw new CustomException(ErrorCode.CSV_VALIDATION_FAILED);
		}
		return new CsvParseResult(
				columnMapping.normalizedMapping(),
				List.copyOf(feedbacks),
				List.copyOf(validationErrors),
				totalCount,
				invalidCount
		);
	}

	private ValidatedColumnMapping validateColumnMapping(
			Map<String, String> requestedMapping,
			List<String> actualHeaders
	) {
		if (requestedMapping == null || requestedMapping.isEmpty() || actualHeaders.isEmpty()) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		Map<String, String> headerLookup = new LinkedHashMap<>();
		for (String actualHeader : actualHeaders) {
			String normalizedHeader = normalizeHeader(actualHeader);
			if (normalizedHeader.isBlank() || headerLookup.put(normalizedHeader, actualHeader) != null) {
				throw new CustomException(ErrorCode.CSV_VALIDATION_FAILED);
			}
		}

		Map<FeedbackCsvField, String> headerByField = new EnumMap<>(FeedbackCsvField.class);
		Map<String, String> normalizedMapping = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : requestedMapping.entrySet()) {
			String requestedHeader = normalizeHeader(entry.getKey());
			String targetName = normalizeNullable(entry.getValue());
			if (requestedHeader.isBlank() || targetName == null) {
				throw new CustomException(ErrorCode.INVALID_REQUEST);
			}
			FeedbackCsvField field = FeedbackCsvField.fromExternalName(targetName);
			String actualHeader = headerLookup.get(requestedHeader);
			if (actualHeader == null) {
				throw new CustomException(ErrorCode.CSV_VALIDATION_FAILED);
			}
			if (headerByField.put(field, actualHeader) != null) {
				throw new CustomException(ErrorCode.INVALID_REQUEST);
			}
			normalizedMapping.put(requestedHeader, field.externalName());
		}

		if (!headerByField.containsKey(FeedbackCsvField.CONTENT)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return new ValidatedColumnMapping(
				Map.copyOf(headerByField),
				Map.copyOf(normalizedMapping)
		);
	}

	private BigDecimal parseRating(
			MappedValue mappedValue,
			int rowNumber,
			Map<String, String> rawRow,
			List<RowValidationError> rowErrors
	) {
		String value = normalizeNullable(mappedValue.value());
		if (value == null) {
			return null;
		}
		try {
			BigDecimal rating = new BigDecimal(value);
			if (rating.compareTo(MIN_RATING) < 0
					|| rating.compareTo(MAX_RATING) > 0
					|| rating.stripTrailingZeros().scale() > 1) {
				throw new NumberFormatException();
			}
			return rating;
		} catch (NumberFormatException exception) {
			rowErrors.add(error(
					rowNumber,
					FeedbackCsvField.RATING,
					DatasetValidationErrorCode.INVALID_RATING_RANGE,
					"rating must be a number between 0 and 5 with at most one decimal place",
					rawRow
			));
			return null;
		}
	}

	private LocalDateTime parseFeedbackCreatedAt(
			MappedValue mappedValue,
			int rowNumber,
			Map<String, String> rawRow,
			List<RowValidationError> rowErrors
	) {
		String value = normalizeNullable(mappedValue.value());
		if (value == null) {
			return null;
		}

		for (DateTimeFormatter formatter : List.of(
				DateTimeFormatter.ISO_LOCAL_DATE_TIME,
				SPACE_SEPARATED_DATE_TIME
		)) {
			try {
				return LocalDateTime.parse(value, formatter);
			} catch (DateTimeParseException ignored) {
				// Try the next documented format.
			}
		}
		try {
			return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
		} catch (DateTimeParseException exception) {
			rowErrors.add(error(
					rowNumber,
					FeedbackCsvField.FEEDBACK_CREATED_AT,
					DatasetValidationErrorCode.INVALID_DATE_FORMAT,
					"feedback_created_at must use ISO date or date-time format",
					rawRow
			));
			return null;
		}
	}

	private String mappedText(
			CSVRecord record,
			ValidatedColumnMapping columnMapping,
			FeedbackCsvField field
	) {
		return normalizeNullable(mappedValue(record, columnMapping, field).value());
	}

	private MappedValue mappedValue(
			CSVRecord record,
			ValidatedColumnMapping columnMapping,
			FeedbackCsvField field
	) {
		String header = columnMapping.headerByField().get(field);
		if (header == null || !record.isSet(header)) {
			return new MappedValue(false, null);
		}
		return new MappedValue(true, record.get(header));
	}

	private Map<String, String> rawRow(CSVRecord record, List<String> actualHeaders) {
		Map<String, String> rawRow = new LinkedHashMap<>();
		for (String actualHeader : actualHeaders) {
			rawRow.put(
					normalizeHeader(actualHeader),
					record.isSet(actualHeader) ? record.get(actualHeader) : ""
			);
		}
		return Map.copyOf(rawRow);
	}

	private RowValidationError error(
			int rowNumber,
			FeedbackCsvField field,
			DatasetValidationErrorCode errorCode,
			String message,
			Map<String, String> rawRow
	) {
		return new RowValidationError(
				rowNumber,
				field.externalName(),
				errorCode,
				message,
				rawRow
		);
	}

	private static String normalizeHeader(String value) {
		if (value == null) {
			return "";
		}
		String normalized = value.trim();
		if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
			normalized = normalized.substring(1).trim();
		}
		return normalized;
	}

	private static String normalizeNullable(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private record ValidatedColumnMapping(
			Map<FeedbackCsvField, String> headerByField,
			Map<String, String> normalizedMapping
	) {
	}

	private record MappedValue(boolean present, String value) {
	}

	public record CsvParseResult(
			Map<String, String> columnMapping,
			List<ParsedFeedback> feedbacks,
			List<RowValidationError> validationErrors,
			int totalCount,
			int invalidCount
	) {
	}

	public record ParsedFeedback(
			String externalId,
			String customerSegment,
			String productName,
			BigDecimal rating,
			String content,
			String language,
			LocalDateTime feedbackCreatedAt
	) {
	}

	public record RowValidationError(
			int rowNumber,
			String fieldName,
			DatasetValidationErrorCode errorCode,
			String message,
			Map<String, String> rawRow
	) {
	}
}
