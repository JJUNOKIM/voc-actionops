package com.vocactionops.backend.common.response;

import com.vocactionops.backend.common.exception.ErrorCode;

import java.util.List;

public record ErrorResponse(
		boolean success,
		Void data,
		String message,
		ErrorDetail error
) {

	public static ErrorResponse from(ErrorCode errorCode) {
		return from(errorCode, List.of());
	}

	public static ErrorResponse from(ErrorCode errorCode, List<ValidationError> details) {
		return new ErrorResponse(
				false,
				null,
				errorCode.message(),
				new ErrorDetail(errorCode.code(), details)
		);
	}

	public record ErrorDetail(
			String code,
			List<ValidationError> details
	) {
		public ErrorDetail {
			details = List.copyOf(details);
		}
	}

	public record ValidationError(
			String field,
			String message
	) {
	}
}
