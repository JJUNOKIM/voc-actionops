package com.vocactionops.backend.analysis.job.application;

import com.vocactionops.backend.analysis.client.AiWorkerClient;
import com.vocactionops.backend.analysis.client.AiWorkerException;
import com.vocactionops.backend.analysis.config.AiWorkerProperties;
import com.vocactionops.backend.analysis.job.application.AnalysisJobExecutionService.ClaimedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AnalysisJobExecutor {

	private static final Logger log = LoggerFactory.getLogger(AnalysisJobExecutor.class);

	private final AnalysisJobExecutionService executionService;
	private final AiWorkerClient aiWorkerClient;
	private final AiWorkerProperties properties;

	public AnalysisJobExecutor(
			AnalysisJobExecutionService executionService,
			AiWorkerClient aiWorkerClient,
			AiWorkerProperties properties
	) {
		this.executionService = executionService;
		this.aiWorkerClient = aiWorkerClient;
		this.properties = properties;
	}

	@Async("analysisTaskExecutor")
	public void execute(String jobId) {
		try {
			if (!executionService.begin(jobId)) {
				return;
			}
			while (processNext(jobId)) {
				// Continue until every persisted item reaches a terminal state.
			}
			executionService.complete(jobId);
		} catch (RuntimeException exception) {
			log.error("Analysis job failed. jobId={}", jobId, exception);
			try {
				executionService.failJob(jobId, "Analysis job was interrupted");
			} catch (RuntimeException failureUpdateException) {
				log.error("Failed to persist analysis job failure. jobId={}", jobId, failureUpdateException);
			}
		}
	}

	private boolean processNext(String jobId) {
		Optional<ClaimedItem> optionalItem = executionService.claimNext(
				jobId,
				properties.modelName()
		);
		if (optionalItem.isEmpty()) {
			return false;
		}

		ClaimedItem item = optionalItem.get();
		if (!item.requiresWorkerCall()) {
			return true;
		}

		try {
			AiWorkerClient.AnalysisResult result = aiWorkerClient.analyze(item.request());
			executionService.completeItem(jobId, item.itemId(), result);
		} catch (AiWorkerException exception) {
			log.warn(
					"Feedback analysis attempt failed. jobId={}, feedbackId={}",
					jobId,
					item.request().feedbackId(),
					exception
			);
			executionService.failAttempt(
					jobId,
					item.itemId(),
					exception.getMessage(),
					properties.maxAttempts()
			);
		}
		return true;
	}
}
