package com.vocactionops.backend.dashboard.application;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dashboard.domain.IssueMetricsSnapshot;
import com.vocactionops.backend.dashboard.repository.DashboardQueryRepository;
import com.vocactionops.backend.dashboard.repository.DashboardQueryRepository.IssueSnapshotMetrics;
import com.vocactionops.backend.dashboard.repository.IssueMetricsSnapshotRepository;
import com.vocactionops.backend.issue.domain.Issue;
import com.vocactionops.backend.issue.repository.IssueRepository;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IssueMetricsSnapshotService {

	private static final ZoneId SNAPSHOT_ZONE = ZoneId.of("Asia/Seoul");

	private final DashboardQueryRepository dashboardQueryRepository;
	private final IssueMetricsSnapshotRepository snapshotRepository;
	private final IssueRepository issueRepository;
	private final OrganizationRepository organizationRepository;
	private final Clock clock;

	public IssueMetricsSnapshotService(
			DashboardQueryRepository dashboardQueryRepository,
			IssueMetricsSnapshotRepository snapshotRepository,
			IssueRepository issueRepository,
			OrganizationRepository organizationRepository,
			Clock clock
	) {
		this.dashboardQueryRepository = dashboardQueryRepository;
		this.snapshotRepository = snapshotRepository;
		this.issueRepository = issueRepository;
		this.organizationRepository = organizationRepository;
		this.clock = clock;
	}

	@Transactional
	@PreAuthorize("hasAnyRole('ADMIN', 'PM')")
	public SnapshotCaptureResult refreshToday(AuthenticatedUser authenticatedUser) {
		return captureOrganization(authenticatedUser.organizationId(), currentDate());
	}

	@Transactional
	public SnapshotCaptureResult captureOrganization(
			Long organizationId,
			LocalDate snapshotDate
	) {
		organizationRepository.findByIdForUpdate(organizationId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		List<IssueSnapshotMetrics> metrics = dashboardQueryRepository.getIssueSnapshotMetrics(
				organizationId
		);
		Map<Long, IssueMetricsSnapshot> existingByIssueId = new HashMap<>();
		for (IssueMetricsSnapshot snapshot : snapshotRepository.findAllByOrganizationAndDate(
				organizationId,
				snapshotDate
		)) {
			existingByIssueId.put(snapshot.getIssue().getId(), snapshot);
		}

		List<IssueMetricsSnapshot> snapshots = new ArrayList<>(metrics.size());
		for (IssueSnapshotMetrics current : metrics) {
			IssueMetricsSnapshot snapshot = existingByIssueId.get(current.issueId());
			if (snapshot == null) {
				snapshot = new IssueMetricsSnapshot(
						issueRepository.getReferenceById(current.issueId()),
						snapshotDate
				);
			}
			snapshot.update(
					current.feedbackCount(),
					current.analyzedFeedbackCount(),
					current.negativeFeedbackCount(),
					current.averageSentimentScore(),
					current.averageUrgencyScore(),
					current.priorityScore(),
					current.unresolvedActionCount()
			);
			snapshots.add(snapshot);
		}
		snapshotRepository.saveAll(snapshots);
		return new SnapshotCaptureResult(snapshotDate, snapshots.size());
	}

	@Transactional(readOnly = true)
	@PreAuthorize("hasAnyRole('ADMIN', 'PM', 'CS', 'VIEWER')")
	public IssueTrendView getIssueTrend(
			AuthenticatedUser authenticatedUser,
			Long issueId,
			LocalDate from,
			LocalDate to
	) {
		Long organizationId = authenticatedUser.organizationId();
		Issue issue = issueRepository.findByIdAndOrganizationId(issueId, organizationId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		IssueTrendDateRange dateRange = IssueTrendDateRange.from(from, to, currentDate());
		List<IssueMetricPoint> points = snapshotRepository.findTrend(
				organizationId,
				issueId,
				dateRange.from(),
				dateRange.to()
		).stream().map(IssueMetricsSnapshotService::toPoint).toList();

		return new IssueTrendView(
				issue.getId(),
				issue.getTitle(),
				issue.getCategory(),
				issue.getStatus(),
				issue.getResolvedAt(),
				dateRange.from(),
				dateRange.to(),
				latestGrowthRate(points),
				points
		);
	}

	public LocalDate currentDate() {
		return LocalDate.now(clock.withZone(SNAPSHOT_ZONE));
	}

	private static IssueMetricPoint toPoint(IssueMetricsSnapshot snapshot) {
		return new IssueMetricPoint(
				snapshot.getSnapshotDate(),
				snapshot.getFeedbackCount(),
				snapshot.getAnalyzedFeedbackCount(),
				DashboardMetricCalculator.percentage(
						snapshot.getNegativeFeedbackCount(),
						snapshot.getAnalyzedFeedbackCount()
				),
				snapshot.getAverageSentimentScore(),
				snapshot.getAverageUrgencyScore(),
				snapshot.getPriorityScore(),
				snapshot.getUnresolvedActionCount()
		);
	}

	private static BigDecimal latestGrowthRate(List<IssueMetricPoint> points) {
		if (points.size() < 2) {
			return null;
		}
		IssueMetricPoint previous = points.get(points.size() - 2);
		IssueMetricPoint latest = points.get(points.size() - 1);
		return DashboardMetricCalculator.growthRate(
				latest.feedbackCount(),
				previous.feedbackCount()
		);
	}
}
