package com.vocactionops.backend.dashboard.application;

import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

record DashboardDateRange(
		LocalDateTime fromDate,
		LocalDateTime toDateExclusive
) {
	static DashboardDateRange from(LocalDate from, LocalDate to) {
		if ((from != null && to != null && from.isAfter(to)) || LocalDate.MAX.equals(to)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return new DashboardDateRange(
				from == null ? null : from.atStartOfDay(),
				to == null ? null : to.plusDays(1).atStartOfDay()
		);
	}
}
