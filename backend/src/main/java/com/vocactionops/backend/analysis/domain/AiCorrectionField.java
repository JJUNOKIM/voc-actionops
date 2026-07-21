package com.vocactionops.backend.analysis.domain;

import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;

import java.util.Arrays;

public enum AiCorrectionField {
	SENTIMENT("sentiment"),
	CATEGORY("category"),
	URGENCY_SCORE("urgency_score");

	private final String externalName;

	AiCorrectionField(String externalName) {
		this.externalName = externalName;
	}

	public String externalName() {
		return externalName;
	}

	public static AiCorrectionField fromExternalName(String value) {
		return Arrays.stream(values())
				.filter(field -> field.externalName.equals(value))
				.findFirst()
				.orElseThrow(() -> new CustomException(ErrorCode.INVALID_REQUEST));
	}
}
