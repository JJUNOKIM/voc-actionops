package com.vocactionops.backend.analysis.job.application;

import com.vocactionops.backend.analysis.job.domain.AnalysisJobStatus;
import com.vocactionops.backend.analysis.job.repository.AnalysisJobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AnalysisJobRecovery {

	private static final List<AnalysisJobStatus> ACTIVE_STATUSES = List.of(
			AnalysisJobStatus.PENDING,
			AnalysisJobStatus.RUNNING
	);

	private final AnalysisJobRepository jobRepository;
	private final AnalysisJobExecutor executor;

	public AnalysisJobRecovery(
			AnalysisJobRepository jobRepository,
			AnalysisJobExecutor executor
	) {
		this.jobRepository = jobRepository;
		this.executor = executor;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void recoverActiveJobs() {
		jobRepository.findIdsByStatusIn(ACTIVE_STATUSES)
				.forEach(executor::execute);
	}
}
