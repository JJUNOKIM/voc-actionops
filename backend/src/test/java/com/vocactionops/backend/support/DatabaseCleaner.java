package com.vocactionops.backend.support;

import com.vocactionops.backend.action.repository.ActionRepository;
import com.vocactionops.backend.analysis.repository.AiCorrectionRepository;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.auth.repository.RefreshTokenRepository;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.dataset.repository.DatasetValidationErrorRepository;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.issue.repository.IssueFeedbackRepository;
import com.vocactionops.backend.issue.repository.IssueRepository;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
import com.vocactionops.backend.user.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DatabaseCleaner {

	private final ActionRepository actionRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final IssueFeedbackRepository issueFeedbackRepository;
	private final IssueRepository issueRepository;
	private final AiCorrectionRepository correctionRepository;
	private final FeedbackAnalysisRepository analysisRepository;
	private final DatasetValidationErrorRepository validationErrorRepository;
	private final FeedbackRepository feedbackRepository;
	private final DatasetRepository datasetRepository;
	private final UserRepository userRepository;
	private final OrganizationRepository organizationRepository;

	public DatabaseCleaner(
			ActionRepository actionRepository,
			RefreshTokenRepository refreshTokenRepository,
			IssueFeedbackRepository issueFeedbackRepository,
			IssueRepository issueRepository,
			AiCorrectionRepository correctionRepository,
			FeedbackAnalysisRepository analysisRepository,
			DatasetValidationErrorRepository validationErrorRepository,
			FeedbackRepository feedbackRepository,
			DatasetRepository datasetRepository,
			UserRepository userRepository,
			OrganizationRepository organizationRepository
	) {
		this.actionRepository = actionRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.issueFeedbackRepository = issueFeedbackRepository;
		this.issueRepository = issueRepository;
		this.correctionRepository = correctionRepository;
		this.analysisRepository = analysisRepository;
		this.validationErrorRepository = validationErrorRepository;
		this.feedbackRepository = feedbackRepository;
		this.datasetRepository = datasetRepository;
		this.userRepository = userRepository;
		this.organizationRepository = organizationRepository;
	}

	@Transactional
	public void clean() {
		refreshTokenRepository.deleteAll();
		actionRepository.deleteAll();
		issueFeedbackRepository.deleteAll();
		issueRepository.deleteAll();
		correctionRepository.deleteAll();
		analysisRepository.deleteAll();
		validationErrorRepository.deleteAll();
		feedbackRepository.deleteAll();
		datasetRepository.deleteAll();
		userRepository.deleteAll();
		organizationRepository.deleteAll();
	}
}
