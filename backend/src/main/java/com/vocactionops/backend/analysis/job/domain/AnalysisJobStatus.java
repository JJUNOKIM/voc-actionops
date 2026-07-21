package com.vocactionops.backend.analysis.job.domain;

public enum AnalysisJobStatus {
	PENDING,
	RUNNING,
	COMPLETED,
	COMPLETED_WITH_ERRORS,
	FAILED;

	public boolean isActive() {
		return this == PENDING || this == RUNNING;
	}
}
