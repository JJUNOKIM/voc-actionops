package com.vocactionops.backend.analysis.job.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AnalysisJobEventListener {

	private final AnalysisJobExecutor executor;

	public AnalysisJobEventListener(AnalysisJobExecutor executor) {
		this.executor = executor;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(AnalysisJobCreatedEvent event) {
		executor.execute(event.jobId());
	}
}
