package com.vocactionops.backend.dashboard.application;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dashboard.repository.DashboardQueryRepository;
import com.vocactionops.backend.dashboard.repository.DashboardQueryRepository.FeedbackMetrics;
import com.vocactionops.backend.dashboard.repository.DashboardQueryRepository.IssueMetrics;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'PM', 'CS', 'VIEWER')")
public class DashboardSummaryService {

	private static final int METRIC_SCALE = 2;

	private final DashboardQueryRepository dashboardQueryRepository;

	public DashboardSummaryService(DashboardQueryRepository dashboardQueryRepository) {
		this.dashboardQueryRepository = dashboardQueryRepository;
	}

	public DashboardSummary getSummary(
			AuthenticatedUser authenticatedUser,
			LocalDate from,
			LocalDate to
	) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		LocalDateTime fromDate = from == null ? null : from.atStartOfDay();
		LocalDateTime toDateExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
		Long organizationId = authenticatedUser.organizationId();

		FeedbackMetrics feedbackMetrics = dashboardQueryRepository.getFeedbackMetrics(
				organizationId,
				fromDate,
				toDateExclusive
		);
		IssueMetrics issueMetrics = dashboardQueryRepository.getIssueMetrics(
				organizationId,
				fromDate,
				toDateExclusive
		);
		BigDecimal averageResolutionHours = dashboardQueryRepository.getAverageResolutionHours(
				organizationId,
				fromDate,
				toDateExclusive
		);

		return new DashboardSummary(
				feedbackMetrics.totalFeedbackCount(),
				negativeFeedbackRate(feedbackMetrics),
				issueMetrics.newIssueCount(),
				issueMetrics.p0IssueCount(),
				issueMetrics.p1IssueCount(),
				issueMetrics.unresolvedIssueCount(),
				normalize(averageResolutionHours)
		);
	}

	private static BigDecimal negativeFeedbackRate(FeedbackMetrics metrics) {
		if (metrics.analyzedFeedbackCount() == 0) {
			return BigDecimal.ZERO.setScale(METRIC_SCALE);
		}
		return BigDecimal.valueOf(metrics.negativeFeedbackCount())
				.multiply(BigDecimal.valueOf(100))
				.divide(
						BigDecimal.valueOf(metrics.analyzedFeedbackCount()),
						METRIC_SCALE,
						RoundingMode.HALF_UP
				);
	}

	private static BigDecimal normalize(BigDecimal value) {
		return value == null ? null : value.setScale(METRIC_SCALE, RoundingMode.HALF_UP);
	}
}
