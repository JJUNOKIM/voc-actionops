package com.vocactionops.backend.dashboard.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class IssueTrendResolutionDateTests {

	@Test
	void convertsUtcResolutionTimeToTheKoreanSnapshotDate() {
		assertThat(IssueMetricsSnapshotService.resolutionDate(
				LocalDateTime.of(2026, 9, 1, 15, 0), ZoneOffset.UTC
		)).hasToString("2026-09-02");
		assertThat(IssueMetricsSnapshotService.resolutionDate(
				LocalDateTime.of(2026, 9, 1, 14, 59), ZoneOffset.UTC
		)).hasToString("2026-09-01");
	}

	@Test
	void keepsDatesAlreadyRecordedInKoreanTime() {
		assertThat(IssueMetricsSnapshotService.resolutionDate(
				LocalDateTime.of(2026, 9, 1, 23, 59), ZoneId.of("Asia/Seoul")
		)).hasToString("2026-09-01");
	}

	@Test
	void hasNoResolutionDateUntilTheIssueIsResolved() {
		assertThat(IssueMetricsSnapshotService.resolutionDate(null, ZoneOffset.UTC)).isNull();
	}
}
