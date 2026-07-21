package com.vocactionops.backend.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청값이 올바르지 않습니다."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "refresh token이 유효하지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
	DUPLICATED_RESOURCE(HttpStatus.CONFLICT, "이미 존재하는 데이터입니다."),
	CSV_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "CSV 데이터 검증에 실패했습니다."),
	AI_ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 분석 처리에 실패했습니다."),
	INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "변경할 수 없는 상태입니다."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

	private final HttpStatus httpStatus;
	private final String message;

	ErrorCode(HttpStatus httpStatus, String message) {
		this.httpStatus = httpStatus;
		this.message = message;
	}

	public HttpStatus httpStatus() {
		return httpStatus;
	}

	public String code() {
		return name();
	}

	public String message() {
		return message;
	}
}
