package com.vocactionops.backend.analysis.job.domain;

import com.vocactionops.backend.common.entity.BaseTimeEntity;
import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "analysis_jobs")
public class AnalysisJob extends BaseTimeEntity {

	@Id
	@Column(length = 36)
	private String id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "dataset_id", nullable = false)
	private Dataset dataset;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AnalysisJobStatus status;

	@Column(name = "total_count", nullable = false)
	private int totalCount;

	@Column(name = "processed_count", nullable = false)
	private int processedCount;

	@Column(name = "success_count", nullable = false)
	private int successCount;

	@Column(name = "failed_count", nullable = false)
	private int failedCount;

	@Column(name = "failure_reason", length = 1000)
	private String failureReason;

	@Column(name = "started_at")
	private LocalDateTime startedAt;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	protected AnalysisJob() {
	}

	public AnalysisJob(Organization organization, Dataset dataset, int totalCount) {
		this.id = UUID.randomUUID().toString();
		this.organization = Objects.requireNonNull(organization, "organization must not be null");
		this.dataset = Objects.requireNonNull(dataset, "dataset must not be null");
		if (!sameOrganization(dataset.getOrganization(), organization)) {
			throw new IllegalArgumentException("dataset must belong to the job organization");
		}
		if (totalCount < 0) {
			throw new IllegalArgumentException("totalCount must not be negative");
		}
		this.totalCount = totalCount;
		this.status = AnalysisJobStatus.PENDING;
	}

	public void start() {
		if (!status.isActive()) {
			throw new IllegalStateException("only active job can be started");
		}
		status = AnalysisJobStatus.RUNNING;
		failureReason = null;
		if (startedAt == null) {
			startedAt = LocalDateTime.now();
		}
	}

	public void recordSuccess() {
		requireRunningAndRemaining();
		processedCount++;
		successCount++;
	}

	public void recordFailure() {
		requireRunningAndRemaining();
		processedCount++;
		failedCount++;
	}

	public void complete() {
		if (status != AnalysisJobStatus.RUNNING || processedCount != totalCount) {
			throw new IllegalStateException("job cannot be completed");
		}
		status = failedCount == 0
				? AnalysisJobStatus.COMPLETED
				: AnalysisJobStatus.COMPLETED_WITH_ERRORS;
		completedAt = LocalDateTime.now();
	}

	public void fail(String failureReason) {
		if (!status.isActive()) {
			return;
		}
		this.failureReason = requireText(failureReason, 1000);
		this.status = AnalysisJobStatus.FAILED;
		this.completedAt = LocalDateTime.now();
	}

	private void requireRunningAndRemaining() {
		if (status != AnalysisJobStatus.RUNNING || processedCount >= totalCount) {
			throw new IllegalStateException("job progress cannot be updated");
		}
	}

	private static String requireText(String value, int maxLength) {
		if (value == null || value.isBlank() || value.trim().length() > maxLength) {
			throw new IllegalArgumentException("failureReason is invalid");
		}
		return value.trim();
	}

	private static boolean sameOrganization(
			Organization first,
			Organization second
	) {
		return first == second
				|| (first.getId() != null && first.getId().equals(second.getId()));
	}

	public String getId() {
		return id;
	}

	public Organization getOrganization() {
		return organization;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public AnalysisJobStatus getStatus() {
		return status;
	}

	public int getTotalCount() {
		return totalCount;
	}

	public int getProcessedCount() {
		return processedCount;
	}

	public int getSuccessCount() {
		return successCount;
	}

	public int getFailedCount() {
		return failedCount;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}
}
