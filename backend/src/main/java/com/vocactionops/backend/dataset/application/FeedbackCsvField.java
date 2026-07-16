package com.vocactionops.backend.dataset.application;

import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;

import java.util.Arrays;

enum FeedbackCsvField {
	EXTERNAL_ID("external_id"),
	CONTENT("content"),
	CUSTOMER_SEGMENT("customer_segment"),
	PRODUCT_NAME("product_name"),
	RATING("rating"),
	LANGUAGE("language"),
	FEEDBACK_CREATED_AT("feedback_created_at");

	private final String externalName;

	FeedbackCsvField(String externalName) {
		this.externalName = externalName;
	}

	String externalName() {
		return externalName;
	}

	static FeedbackCsvField fromExternalName(String value) {
		return Arrays.stream(values())
				.filter(field -> field.externalName.equals(value))
				.findFirst()
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
	}
}
