package com.vocactionops.backend.dataset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "dataset_validation_errors")
public class DatasetValidationError {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "dataset_id", nullable = false)
	private Dataset dataset;

	@Column(name = "csv_row_number", nullable = false)
	private int rowNumber;

	@Column(name = "field_name", nullable = false, length = 100)
	private String fieldName;

	@Enumerated(EnumType.STRING)
	@Column(name = "error_code", nullable = false, length = 50)
	private DatasetValidationErrorCode errorCode;

	@Column(name = "error_message", nullable = false, length = 500)
	private String errorMessage;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "raw_row_json", nullable = false, columnDefinition = "json")
	private Map<String, String> rawRow;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected DatasetValidationError() {
	}

	public DatasetValidationError(
			Dataset dataset,
			int rowNumber,
			String fieldName,
			DatasetValidationErrorCode errorCode,
			String errorMessage,
			Map<String, String> rawRow
	) {
		this.dataset = Objects.requireNonNull(dataset, "dataset must not be null");
		if (rowNumber < 2) {
			throw new IllegalArgumentException("rowNumber must include the CSV header row");
		}
		this.rowNumber = rowNumber;
		this.fieldName = requireText(fieldName, "fieldName");
		this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
		this.errorMessage = requireText(errorMessage, "errorMessage");
		this.rawRow = Map.copyOf(Objects.requireNonNull(rawRow, "rawRow must not be null"));
	}

	@PrePersist
	private void onCreate() {
		createdAt = LocalDateTime.now();
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value.trim();
	}

	public Long getId() {
		return id;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public int getRowNumber() {
		return rowNumber;
	}

	public String getFieldName() {
		return fieldName;
	}

	public DatasetValidationErrorCode getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public Map<String, String> getRawRow() {
		return Map.copyOf(rawRow);
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
