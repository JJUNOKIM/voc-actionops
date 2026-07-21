package com.vocactionops.backend.analysis.job.application;

import com.vocactionops.backend.analysis.job.domain.AnalysisJob;
import com.vocactionops.backend.analysis.job.domain.AnalysisJobStatus;
import com.vocactionops.backend.dataset.domain.DatasetStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public record AnalysisJobView(
		Long datasetId,
		DatasetStatus status,
		String jobId,
		AnalysisJobStatus jobStatus,
		int totalCount,
		int processedCount,
		int successCount,
		int failedCount,
		BigDecimal progressRate,
		String failureReason,
		LocalDateTime startedAt,
		LocalDateTime completedAt
) {

	public static AnalysisJobView from(AnalysisJob job) {
		return new AnalysisJobView(
				job.getDataset().getId(),
				job.getDataset().getStatus(),
				job.getId(),
				job.getStatus(),
				job.getTotalCount(),
				job.getProcessedCount(),
				job.getSuccessCount(),
				job.getFailedCount(),
				progressRate(job),
				job.getFailureReason(),
				job.getStartedAt(),
				job.getCompletedAt()
		);
	}

	private static BigDecimal progressRate(AnalysisJob job) {
		if (job.getTotalCount() == 0) {
			return job.getStatus().isActive()
					? BigDecimal.ZERO.setScale(1)
					: BigDecimal.valueOf(100).setScale(1);
		}
		return BigDecimal.valueOf(job.getProcessedCount())
				.multiply(BigDecimal.valueOf(100))
				.divide(BigDecimal.valueOf(job.getTotalCount()), 1, RoundingMode.HALF_UP);
	}
}
