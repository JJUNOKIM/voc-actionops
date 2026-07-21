package com.vocactionops.backend.analysis.job.domain;

import com.vocactionops.backend.common.entity.BaseTimeEntity;
import com.vocactionops.backend.feedback.domain.Feedback;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "analysis_job_items")
public class AnalysisJobItem extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "job_id", nullable = false)
	private AnalysisJob job;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "feedback_id", nullable = false)
	private Feedback feedback;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AnalysisJobItemStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "last_error", length = 1000)
	private String lastError;

	protected AnalysisJobItem() {
	}

	public AnalysisJobItem(AnalysisJob job, Feedback feedback) {
		this.job = Objects.requireNonNull(job, "job must not be null");
		this.feedback = Objects.requireNonNull(feedback, "feedback must not be null");
		if (!sameDataset(feedback, job)) {
			throw new IllegalArgumentException("feedback must belong to the job dataset");
		}
		this.status = AnalysisJobItemStatus.PENDING;
	}

	public void startAttempt() {
		if (status != AnalysisJobItemStatus.PENDING) {
			throw new IllegalStateException("only pending item can start");
		}
		status = AnalysisJobItemStatus.RUNNING;
		attemptCount++;
	}

	public void retry(String errorMessage) {
		requireRunning();
		status = AnalysisJobItemStatus.PENDING;
		lastError = requireText(errorMessage);
	}

	public void succeed() {
		requireRunning();
		status = AnalysisJobItemStatus.SUCCESS;
		lastError = null;
	}

	public void fail(String errorMessage) {
		requireRunning();
		status = AnalysisJobItemStatus.FAILED;
		lastError = requireText(errorMessage);
	}

	public void resetInterruptedAttempt() {
		if (status == AnalysisJobItemStatus.RUNNING) {
			status = AnalysisJobItemStatus.PENDING;
		}
	}

	private void requireRunning() {
		if (status != AnalysisJobItemStatus.RUNNING) {
			throw new IllegalStateException("analysis item must be running");
		}
	}

	private static String requireText(String value) {
		if (value == null || value.isBlank() || value.trim().length() > 1000) {
			throw new IllegalArgumentException("errorMessage is invalid");
		}
		return value.trim();
	}

	private static boolean sameDataset(Feedback feedback, AnalysisJob job) {
		if (feedback.getDataset() == job.getDataset()) {
			return true;
		}
		Long feedbackDatasetId = feedback.getDataset().getId();
		return feedbackDatasetId != null
				&& feedbackDatasetId.equals(job.getDataset().getId());
	}

	public Long getId() {
		return id;
	}

	public AnalysisJob getJob() {
		return job;
	}

	public Feedback getFeedback() {
		return feedback;
	}

	public AnalysisJobItemStatus getStatus() {
		return status;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public String getLastError() {
		return lastError;
	}
}
