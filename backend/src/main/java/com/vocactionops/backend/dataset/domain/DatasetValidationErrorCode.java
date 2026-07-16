package com.vocactionops.backend.dataset.domain;

public enum DatasetValidationErrorCode {
	MISSING_REQUIRED_FIELD,
	EMPTY_CONTENT,
	INVALID_RATING_RANGE,
	INVALID_DATE_FORMAT,
	DUPLICATED_EXTERNAL_ID
}
