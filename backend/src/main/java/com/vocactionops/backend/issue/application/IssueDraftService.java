package com.vocactionops.backend.issue.application;

import com.vocactionops.backend.analysis.domain.AnalysisStatus;
import com.vocactionops.backend.analysis.domain.FeedbackAnalysis;
import com.vocactionops.backend.analysis.domain.Sentiment;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.issue.application.IssueDraftGenerator.Draft;
import com.vocactionops.backend.issue.application.IssueService.IssueDetail;
import com.vocactionops.backend.issue.domain.LinkSource;
import com.vocactionops.backend.issue.domain.Priority;
import com.vocactionops.backend.issue.repository.IssueFeedbackRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class IssueDraftService {

	private static final BigDecimal SEED_SIMILARITY_SCORE = new BigDecimal("1.0000");

	private final FeedbackRepository feedbackRepository;
	private final FeedbackAnalysisRepository analysisRepository;
	private final IssueFeedbackRepository issueFeedbackRepository;
	private final IssueCandidateService candidateService;
	private final IssueDraftGenerator draftGenerator;
	private final IssueFeedbackLinkService linkService;
	private final IssueService issueService;

	public IssueDraftService(
			FeedbackRepository feedbackRepository,
			FeedbackAnalysisRepository analysisRepository,
			IssueFeedbackRepository issueFeedbackRepository,
			IssueCandidateService candidateService,
			IssueDraftGenerator draftGenerator,
			IssueFeedbackLinkService linkService,
			IssueService issueService
	) {
		this.feedbackRepository = feedbackRepository;
		this.analysisRepository = analysisRepository;
		this.issueFeedbackRepository = issueFeedbackRepository;
		this.candidateService = candidateService;
		this.draftGenerator = draftGenerator;
		this.linkService = linkService;
		this.issueService = issueService;
	}

	public IssueDraftView getDraft(AuthenticatedUser authenticatedUser, Long feedbackId) {
		Feedback feedback = getFeedback(authenticatedUser.organizationId(), feedbackId, false);
		FeedbackAnalysis analysis = getSuccessfulAnalysis(
				authenticatedUser.organizationId(),
				feedbackId,
				false
		);
		validateDraftAvailability(authenticatedUser, feedbackId);
		Draft draft = draftGenerator.generate(analysis.getSummary(), feedback.getContent());
		return new IssueDraftView(
				feedbackId,
				analysis.getVersion(),
				draft.title(),
				draft.description(),
				analysis.getCategory(),
				analysis.getSentiment(),
				analysis.getUrgencyScore(),
				analysis.getConfidenceScore()
		);
	}

	@Transactional
	@PreAuthorize("hasAnyRole('ADMIN', 'PM')")
	public IssueDetail confirmDraft(
			AuthenticatedUser authenticatedUser,
			Long feedbackId,
			Long analysisVersion,
			String title,
			String description,
			Long assigneeId
	) {
		getFeedback(authenticatedUser.organizationId(), feedbackId, true);
		FeedbackAnalysis analysis = getSuccessfulAnalysis(
				authenticatedUser.organizationId(),
				feedbackId,
				true
		);
		if (analysisVersion == null || analysisVersion < 0) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		if (analysis.getVersion() != analysisVersion) {
			throw new CustomException(ErrorCode.STALE_RESOURCE);
		}
		validateDraftAvailability(authenticatedUser, feedbackId);

		IssueDetail createdIssue = issueService.createIssue(
				authenticatedUser,
				title,
				description,
				analysis.getCategory(),
				Priority.P3,
				assigneeId
		);
		linkService.link(
				authenticatedUser.organizationId(),
				feedbackId,
				createdIssue.id(),
				SEED_SIMILARITY_SCORE,
				true,
				LinkSource.AI
		);
		return issueService.getIssue(authenticatedUser, createdIssue.id());
	}

	private Feedback getFeedback(Long organizationId, Long feedbackId, boolean forUpdate) {
		return (forUpdate
				? feedbackRepository.findByIdAndOrganizationIdForUpdate(feedbackId, organizationId)
				: feedbackRepository.findByIdAndOrganizationId(feedbackId, organizationId))
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
	}

	private FeedbackAnalysis getSuccessfulAnalysis(
			Long organizationId,
			Long feedbackId,
			boolean forUpdate
	) {
		FeedbackAnalysis analysis = (forUpdate
				? analysisRepository.findByFeedbackAndOrganizationForUpdate(feedbackId, organizationId)
				: analysisRepository.findByFeedbackIdAndFeedbackOrganizationId(feedbackId, organizationId))
				.orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_NOT_READY));
		if (analysis.getStatus() != AnalysisStatus.SUCCESS) {
			throw new CustomException(ErrorCode.ANALYSIS_NOT_READY);
		}
		return analysis;
	}

	private void validateDraftAvailability(
			AuthenticatedUser authenticatedUser,
			Long feedbackId
	) {
		if (issueFeedbackRepository.existsByFeedbackId(feedbackId)) {
			throw new CustomException(ErrorCode.DUPLICATED_RESOURCE);
		}
		if (!candidateService.getCandidates(authenticatedUser, feedbackId, 1).isEmpty()) {
			throw new CustomException(ErrorCode.ISSUE_CANDIDATE_EXISTS);
		}
	}

	public record IssueDraftView(
			Long feedbackId,
			long analysisVersion,
			String title,
			String description,
			String category,
			Sentiment sentiment,
			BigDecimal urgencyScore,
			BigDecimal confidenceScore
	) {
	}
}
