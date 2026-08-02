package com.vocactionops.backend.dashboard.application;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dashboard.repository.DashboardQueryRepository;
import com.vocactionops.backend.dashboard.repository.DashboardQueryRepository.CategoryFeedbackMetrics;
import com.vocactionops.backend.dashboard.repository.DashboardQueryRepository.CategoryIssueMetrics;
import com.vocactionops.backend.dashboard.repository.DashboardQueryRepository.TopIssueMetrics;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN', 'PM', 'CS', 'VIEWER')")
public class DashboardInsightsService {

	private static final int MAX_TOP_ISSUES = 50;

	private final DashboardQueryRepository dashboardQueryRepository;

	public DashboardInsightsService(DashboardQueryRepository dashboardQueryRepository) {
		this.dashboardQueryRepository = dashboardQueryRepository;
	}

	public List<CategoryBreakdownItem> getCategoryBreakdown(
			AuthenticatedUser authenticatedUser,
			LocalDate from,
			LocalDate to
	) {
		DashboardDateRange dateRange = DashboardDateRange.from(from, to);
		Long organizationId = authenticatedUser.organizationId();
		Map<String, CategoryAccumulator> categories = new HashMap<>();

		for (CategoryFeedbackMetrics metrics : dashboardQueryRepository.getCategoryFeedbackMetrics(
				organizationId,
				dateRange.fromDate(),
				dateRange.toDateExclusive()
		)) {
			CategoryAccumulator accumulator = categories.computeIfAbsent(
					metrics.category(),
					ignored -> new CategoryAccumulator()
			);
			accumulator.feedbackCount = metrics.feedbackCount();
			accumulator.negativeFeedbackCount = metrics.negativeFeedbackCount();
		}
		for (CategoryIssueMetrics metrics : dashboardQueryRepository.getActiveIssueCountByCategory(
				organizationId
		)) {
			categories.computeIfAbsent(metrics.category(), ignored -> new CategoryAccumulator())
					.issueCount = metrics.issueCount();
		}

		return categories.entrySet().stream()
				.map(entry -> entry.getValue().toView(entry.getKey()))
				.sorted(categoryOrder())
				.toList();
	}

	public List<TopIssueView> getTopIssues(
			AuthenticatedUser authenticatedUser,
			int limit,
			String sortBy
	) {
		if (limit < 1 || limit > MAX_TOP_ISSUES) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		TopIssueSort sort = TopIssueSort.from(sortBy);
		List<TopIssueMetrics> metrics = switch (sort) {
			case PRIORITY_SCORE -> dashboardQueryRepository.getTopIssuesByPriorityScore(
					authenticatedUser.organizationId(),
					limit
			);
			case FEEDBACK_COUNT -> dashboardQueryRepository.getTopIssuesByFeedbackCount(
					authenticatedUser.organizationId(),
					limit
			);
		};
		return metrics.stream()
				.map(DashboardInsightsService::toView)
				.toList();
	}

	private static TopIssueView toView(TopIssueMetrics metrics) {
		return new TopIssueView(
				metrics.issueId(),
				metrics.title(),
				metrics.category(),
				metrics.priority(),
				metrics.priorityScore(),
				metrics.status(),
				metrics.feedbackCount(),
				DashboardMetricCalculator.percentage(
						metrics.negativeFeedbackCount(),
						metrics.analyzedFeedbackCount()
				),
				metrics.unresolvedActionCount(),
				metrics.assigneeId(),
				metrics.assigneeName(),
				metrics.lastSeenAt()
		);
	}

	private static Comparator<CategoryBreakdownItem> categoryOrder() {
		return Comparator.comparingLong(CategoryBreakdownItem::feedbackCount)
				.reversed()
				.thenComparing(
						Comparator.comparingLong(CategoryBreakdownItem::issueCount).reversed()
				)
				.thenComparing(CategoryBreakdownItem::category);
	}

	private static final class CategoryAccumulator {
		private long issueCount;
		private long feedbackCount;
		private long negativeFeedbackCount;

		private CategoryBreakdownItem toView(String category) {
			return new CategoryBreakdownItem(
					category,
					issueCount,
					feedbackCount,
					DashboardMetricCalculator.percentage(
							negativeFeedbackCount,
							feedbackCount
					)
			);
		}
	}

	private enum TopIssueSort {
		PRIORITY_SCORE("priority_score"),
		FEEDBACK_COUNT("feedback_count");

		private final String externalName;

		TopIssueSort(String externalName) {
			this.externalName = externalName;
		}

		private static TopIssueSort from(String value) {
			String normalized = value == null
					? PRIORITY_SCORE.externalName
					: value.trim().toLowerCase(Locale.ROOT);
			for (TopIssueSort sort : values()) {
				if (sort.externalName.equals(normalized)) {
					return sort;
				}
			}
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}
}
