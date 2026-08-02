package com.vocactionops.backend.dashboard.domain;

import com.vocactionops.backend.common.entity.BaseTimeEntity;
import com.vocactionops.backend.issue.domain.Issue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
		name = "issue_metrics_snapshots",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_issue_metrics_snapshots_issue_date",
				columnNames = {"issue_id", "snapshot_date"}
		)
)
public class IssueMetricsSnapshot extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "issue_id", nullable = false)
	private Issue issue;

	@Column(name = "snapshot_date", nullable = false)
	private LocalDate snapshotDate;

	@Column(name = "feedback_count", nullable = false)
	private long feedbackCount;

	@Column(name = "analyzed_feedback_count", nullable = false)
	private long analyzedFeedbackCount;

	@Column(name = "negative_feedback_count", nullable = false)
	private long negativeFeedbackCount;

	@Column(name = "average_sentiment_score", precision = 6, scale = 5)
	private BigDecimal averageSentimentScore;

	@Column(name = "average_urgency_score", precision = 5, scale = 4)
	private BigDecimal averageUrgencyScore;

	@Column(name = "priority_score", precision = 5, scale = 2)
	private BigDecimal priorityScore;

	@Column(name = "unresolved_action_count", nullable = false)
	private long unresolvedActionCount;

	@Version
	@Column(nullable = false)
	private long version;

	protected IssueMetricsSnapshot() {
	}

	public IssueMetricsSnapshot(Issue issue, LocalDate snapshotDate) {
		this.issue = Objects.requireNonNull(issue, "issue must not be null");
		this.snapshotDate = Objects.requireNonNull(
				snapshotDate,
				"snapshotDate must not be null"
		);
	}

	public void update(
			long feedbackCount,
			long analyzedFeedbackCount,
			long negativeFeedbackCount,
			BigDecimal averageSentimentScore,
			BigDecimal averageUrgencyScore,
			BigDecimal priorityScore,
			long unresolvedActionCount
	) {
		validateCounts(
				feedbackCount,
				analyzedFeedbackCount,
				negativeFeedbackCount,
				unresolvedActionCount
		);
		this.feedbackCount = feedbackCount;
		this.analyzedFeedbackCount = analyzedFeedbackCount;
		this.negativeFeedbackCount = negativeFeedbackCount;
		this.averageSentimentScore = normalizeScore(
				averageSentimentScore,
				BigDecimal.valueOf(-1),
				BigDecimal.ONE,
				5,
				"averageSentimentScore"
		);
		this.averageUrgencyScore = normalizeScore(
				averageUrgencyScore,
				BigDecimal.ZERO,
				BigDecimal.ONE,
				4,
				"averageUrgencyScore"
		);
		this.priorityScore = normalizeScore(
				priorityScore,
				BigDecimal.ZERO,
				BigDecimal.valueOf(100),
				2,
				"priorityScore"
		);
		this.unresolvedActionCount = unresolvedActionCount;
	}

	private static void validateCounts(
			long feedbackCount,
			long analyzedFeedbackCount,
			long negativeFeedbackCount,
			long unresolvedActionCount
	) {
		if (feedbackCount < 0
				|| analyzedFeedbackCount < 0
				|| negativeFeedbackCount < 0
				|| unresolvedActionCount < 0
				|| analyzedFeedbackCount > feedbackCount
				|| negativeFeedbackCount > analyzedFeedbackCount) {
			throw new IllegalArgumentException("snapshot counts are invalid");
		}
	}

	private static BigDecimal normalizeScore(
			BigDecimal value,
			BigDecimal minimum,
			BigDecimal maximum,
			int scale,
			String fieldName
	) {
		if (value == null) {
			return null;
		}
		if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return value.setScale(scale, RoundingMode.HALF_UP);
	}

	public Long getId() {
		return id;
	}

	public Issue getIssue() {
		return issue;
	}

	public LocalDate getSnapshotDate() {
		return snapshotDate;
	}

	public long getFeedbackCount() {
		return feedbackCount;
	}

	public long getAnalyzedFeedbackCount() {
		return analyzedFeedbackCount;
	}

	public long getNegativeFeedbackCount() {
		return negativeFeedbackCount;
	}

	public BigDecimal getAverageSentimentScore() {
		return averageSentimentScore;
	}

	public BigDecimal getAverageUrgencyScore() {
		return averageUrgencyScore;
	}

	public BigDecimal getPriorityScore() {
		return priorityScore;
	}

	public long getUnresolvedActionCount() {
		return unresolvedActionCount;
	}

	public long getVersion() {
		return version;
	}
}
