package com.vocactionops.backend.analysis.job.application;

import com.vocactionops.backend.analysis.job.domain.AnalysisJob;
import com.vocactionops.backend.analysis.job.repository.AnalysisJobRepository;
import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisJobService {

	private final DatasetRepository datasetRepository;
	private final FeedbackRepository feedbackRepository;
	private final AnalysisJobRepository jobRepository;
	private final ApplicationEventPublisher eventPublisher;

	public AnalysisJobService(
			DatasetRepository datasetRepository,
			FeedbackRepository feedbackRepository,
			AnalysisJobRepository jobRepository,
			ApplicationEventPublisher eventPublisher
	) {
		this.datasetRepository = datasetRepository;
		this.feedbackRepository = feedbackRepository;
		this.jobRepository = jobRepository;
		this.eventPublisher = eventPublisher;
	}

	@Transactional
	@PreAuthorize("hasAnyRole('ADMIN', 'PM')")
	public AnalysisJobView start(
			AuthenticatedUser authenticatedUser,
			Long datasetId
	) {
		Dataset dataset = datasetRepository.findByIdAndOrganizationIdForUpdate(
					datasetId,
					authenticatedUser.organizationId()
			)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		try {
			dataset.startAnalysis();
		} catch (IllegalStateException exception) {
			throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
		}

		int totalCount = Math.toIntExact(feedbackRepository
				.countByDatasetIdAndOrganizationId(datasetId, authenticatedUser.organizationId()));
		AnalysisJob job = jobRepository.save(new AnalysisJob(
				dataset.getOrganization(),
				dataset,
				totalCount
		));
		eventPublisher.publishEvent(new AnalysisJobCreatedEvent(job.getId()));
		return AnalysisJobView.from(job);
	}

	@Transactional(readOnly = true)
	@PreAuthorize("hasAnyRole('ADMIN', 'PM', 'CS', 'VIEWER')")
	public AnalysisJobView getStatus(
			AuthenticatedUser authenticatedUser,
			Long datasetId
	) {
		return jobRepository.findTopByDatasetIdAndOrganizationIdOrderByCreatedAtDesc(
					datasetId,
					authenticatedUser.organizationId()
			)
				.map(AnalysisJobView::from)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
	}
}
