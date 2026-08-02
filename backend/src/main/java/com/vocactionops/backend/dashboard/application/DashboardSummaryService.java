package com.vocactionops.backend.dashboard.application;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.dashboard.repository.DashboardQueryRepository;
import com.vocactionops.backend.dashboard.repository.DashboardQueryRepository.FeedbackMetrics;
import com.vocactionops.backend.dashboard.repository.DashboardQueryRepository.IssueMetrics;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'PM', 'CS', 'VIEWER')")
public class DashboardSummaryService {

	private final DashboardQueryRepository dashboardQueryRepository;

	public DashboardSummaryService(DashboardQueryRepository dashboardQueryRepository) {
		this.dashboardQueryRepository = dashboardQueryRepository;
	}

	public DashboardSummary getSummary(
			AuthenticatedUser authenticatedUser,
			LocalDate from,
			LocalDate to
	) {
		DashboardDateRange dateRange = DashboardDateRange.from(from, to);
		Long organizationId = authenticatedUser.organizationId();

		FeedbackMetrics feedbackMetrics = dashboardQueryRepository.getFeedbackMetrics(
				organizationId,
				dateRange.fromDate(),
				dateRange.toDateExclusive()
		);
		IssueMetrics issueMetrics = dashboardQueryRepository.getIssueMetrics(
				organizationId,
				dateRange.fromDate(),
				dateRange.toDateExclusive()
		);
		BigDecimal averageResolutionHours = dashboardQueryRepository.getAverageResolutionHours(
				organizationId,
				dateRange.fromDate(),
				dateRange.toDateExclusive()
		);

		return new DashboardSummary(
				feedbackMetrics.totalFeedbackCount(),
				DashboardMetricCalculator.percentage(
						feedbackMetrics.negativeFeedbackCount(),
						feedbackMetrics.analyzedFeedbackCount()
				),
				issueMetrics.newIssueCount(),
				issueMetrics.p0IssueCount(),
				issueMetrics.p1IssueCount(),
				issueMetrics.unresolvedIssueCount(),
				DashboardMetricCalculator.normalize(averageResolutionHours)
		);
	}
}
