package com.vocactionops.backend.analysis.job.application;

import com.vocactionops.backend.analysis.application.FeedbackAnalysisService;
import com.vocactionops.backend.analysis.application.FeedbackAnalysisService.AnalysisResult;
import com.vocactionops.backend.analysis.client.AiWorkerClient;
import com.vocactionops.backend.analysis.client.AiWorkerClient.AnalysisRequest;
import com.vocactionops.backend.analysis.domain.AnalysisStatus;
import com.vocactionops.backend.analysis.job.domain.AnalysisJob;
import com.vocactionops.backend.analysis.job.domain.AnalysisJobItem;
import com.vocactionops.backend.analysis.job.domain.AnalysisJobItemStatus;
import com.vocactionops.backend.analysis.job.domain.AnalysisJobStatus;
import com.vocactionops.backend.analysis.job.repository.AnalysisJobItemRepository;
import com.vocactionops.backend.analysis.job.repository.AnalysisJobRepository;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AnalysisJobExecutionService {

	private final AnalysisJobRepository jobRepository;
	private final AnalysisJobItemRepository itemRepository;
	private final FeedbackRepository feedbackRepository;
	private final FeedbackAnalysisRepository analysisRepository;
	private final FeedbackAnalysisService analysisService;

	public AnalysisJobExecutionService(
			AnalysisJobRepository jobRepository,
			AnalysisJobItemRepository itemRepository,
			FeedbackRepository feedbackRepository,
			FeedbackAnalysisRepository analysisRepository,
			FeedbackAnalysisService analysisService
	) {
		this.jobRepository = jobRepository;
		this.itemRepository = itemRepository;
		this.feedbackRepository = feedbackRepository;
		this.analysisRepository = analysisRepository;
		this.analysisService = analysisService;
	}

	@Transactional
	public boolean begin(String jobId) {
		AnalysisJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);
		if (job == null || !job.getStatus().isActive()) {
			return false;
		}
		job.start();
		initializeItems(job);
		itemRepository.findByJobIdAndStatus(jobId, AnalysisJobItemStatus.RUNNING)
				.forEach(AnalysisJobItem::resetInterruptedAttempt);
		return true;
	}

	@Transactional
	public Optional<ClaimedItem> claimNext(String jobId, String modelName) {
		AnalysisJob job = getRunningJob(jobId);
		Optional<AnalysisJobItem> optionalItem = itemRepository
				.findFirstByJobIdAndStatusOrderById(jobId, AnalysisJobItemStatus.PENDING);
		if (optionalItem.isEmpty()) {
			return Optional.empty();
		}

		AnalysisJobItem item = optionalItem.get();
		item.startAttempt();
		Feedback feedback = item.getFeedback();
		AnalysisStatus currentStatus = analysisRepository
				.findByFeedbackIdAndFeedbackOrganizationId(
						feedback.getId(),
						job.getOrganization().getId()
				)
				.map(analysis -> analysis.getStatus())
				.orElse(null);

		if (currentStatus == AnalysisStatus.SUCCESS) {
			item.succeed();
			job.recordSuccess();
			return Optional.of(ClaimedItem.alreadyCompleted(item.getId()));
		}
		if (currentStatus == null || currentStatus == AnalysisStatus.FAILED) {
			analysisService.startAnalysis(
					job.getOrganization().getId(),
					feedback.getId(),
					modelName
			);
		}

		return Optional.of(ClaimedItem.forWorker(item.getId(), new AnalysisRequest(
				feedback.getId(),
				feedback.getContent(),
				feedback.getRating(),
				feedback.getLanguage(),
				feedback.getProductName(),
				feedback.getCustomerSegment()
		)));
	}

	@Transactional
	public void completeItem(
			String jobId,
			Long itemId,
			AiWorkerClient.AnalysisResult workerResult
	) {
		AnalysisJob job = getRunningJob(jobId);
		AnalysisJobItem item = getRunningItem(jobId, itemId);
		analysisService.completeAnalysis(
				job.getOrganization().getId(),
				item.getFeedback().getId(),
				new AnalysisResult(
						workerResult.sentiment(),
						workerResult.sentimentScore(),
						workerResult.category(),
						workerResult.urgencyScore(),
						workerResult.summary(),
						workerResult.confidenceScore(),
						workerResult.modelName()
				)
		);
		item.succeed();
		job.recordSuccess();
	}

	@Transactional
	public void failAttempt(
			String jobId,
			Long itemId,
			String errorMessage,
			int maxAttempts
	) {
		AnalysisJob job = getRunningJob(jobId);
		AnalysisJobItem item = getRunningItem(jobId, itemId);
		Long organizationId = job.getOrganization().getId();
		Long feedbackId = item.getFeedback().getId();
		AnalysisStatus currentStatus = analysisRepository
				.findByFeedbackIdAndFeedbackOrganizationId(feedbackId, organizationId)
				.map(analysis -> analysis.getStatus())
				.orElse(null);

		if (currentStatus == AnalysisStatus.SUCCESS) {
			item.succeed();
			job.recordSuccess();
			return;
		}
		if (currentStatus == AnalysisStatus.PENDING) {
			analysisService.failAnalysis(organizationId, feedbackId, safeMessage(errorMessage));
		}

		if (item.getAttemptCount() < maxAttempts) {
			item.retry(safeMessage(errorMessage));
		} else {
			item.fail(safeMessage(errorMessage));
			job.recordFailure();
		}
	}

	@Transactional
	public void complete(String jobId) {
		AnalysisJob job = getRunningJob(jobId);
		job.complete();
		job.getDataset().completeAnalysis(job.getFailedCount() > 0);
	}

	@Transactional
	public void failJob(String jobId, String failureReason) {
		AnalysisJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);
		if (job == null || !job.getStatus().isActive()) {
			return;
		}
		job.fail(safeMessage(failureReason));
		job.getDataset().failAnalysis();
	}

	private void initializeItems(AnalysisJob job) {
		if (itemRepository.countByJobId(job.getId()) > 0) {
			return;
		}
		List<Feedback> feedbacks = feedbackRepository
				.findAllByDatasetIdAndOrganizationIdOrderById(
						job.getDataset().getId(),
						job.getOrganization().getId()
				);
		if (feedbacks.size() != job.getTotalCount()) {
			throw new IllegalStateException("analysis job feedback count changed");
		}
		itemRepository.saveAll(feedbacks.stream()
				.map(feedback -> new AnalysisJobItem(job, feedback))
				.toList());
	}

	private AnalysisJob getRunningJob(String jobId) {
		AnalysisJob job = jobRepository.findByIdForUpdate(jobId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		if (job.getStatus() != AnalysisJobStatus.RUNNING) {
			throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
		}
		return job;
	}

	private AnalysisJobItem getRunningItem(String jobId, Long itemId) {
		AnalysisJobItem item = itemRepository.findByIdAndJobId(itemId, jobId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		if (item.getStatus() != AnalysisJobItemStatus.RUNNING) {
			throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
		}
		return item;
	}

	private static String safeMessage(String value) {
		if (value == null || value.isBlank()) {
			return "AI analysis failed";
		}
		String normalized = value.trim();
		return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
	}

	public record ClaimedItem(Long itemId, AnalysisRequest request) {

		static ClaimedItem alreadyCompleted(Long itemId) {
			return new ClaimedItem(itemId, null);
		}

		static ClaimedItem forWorker(Long itemId, AnalysisRequest request) {
			return new ClaimedItem(itemId, request);
		}

		public boolean requiresWorkerCall() {
			return request != null;
		}
	}
}
