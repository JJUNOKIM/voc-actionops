package com.vocactionops.backend.dashboard.application;

import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

record IssueTrendDateRange(LocalDate from, LocalDate to) {

	private static final long MAX_DAYS = 366;
	private static final long DEFAULT_DAYS = 30;

	static IssueTrendDateRange from(LocalDate from, LocalDate to, LocalDate today) {
		LocalDate end = to == null ? today : to;
		LocalDate start;
		try {
			start = from == null ? end.minusDays(DEFAULT_DAYS - 1) : from;
		} catch (DateTimeException exception) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return new IssueTrendDateRange(start, end);
	}
}
