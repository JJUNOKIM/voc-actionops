package com.vocactionops.backend.issue.application;

import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.issue.domain.Issue;
import com.vocactionops.backend.issue.domain.IssueFeedback;
import com.vocactionops.backend.issue.domain.LinkSource;
import com.vocactionops.backend.issue.repository.IssueFeedbackRepository;
import com.vocactionops.backend.issue.repository.IssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class IssueFeedbackLinkService {

	private final FeedbackRepository feedbackRepository;
	private final IssueRepository issueRepository;
	private final IssueFeedbackRepository issueFeedbackRepository;
	private final IssuePriorityScoringService priorityScoringService;

	public IssueFeedbackLinkService(
			FeedbackRepository feedbackRepository,
			IssueRepository issueRepository,
			IssueFeedbackRepository issueFeedbackRepository,
			IssuePriorityScoringService priorityScoringService
	) {
		this.feedbackRepository = feedbackRepository;
		this.issueRepository = issueRepository;
		this.issueFeedbackRepository = issueFeedbackRepository;
		this.priorityScoringService = priorityScoringService;
	}

	@Transactional
	public IssueFeedback link(
			Long organizationId,
			Long feedbackId,
			Long issueId,
			BigDecimal similarityScore,
			boolean representative,
			LinkSource linkSource
	) {
		Feedback feedback = feedbackRepository.findByIdAndOrganizationIdForUpdate(feedbackId, organizationId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		Issue issue = issueRepository.findByIdAndOrganizationIdForUpdate(issueId, organizationId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		if (issueFeedbackRepository.existsByIssueIdAndFeedbackId(issueId, feedbackId)) {
			throw new CustomException(ErrorCode.DUPLICATED_RESOURCE);
		}
		try {
			IssueFeedback link = issueFeedbackRepository.save(new IssueFeedback(
					issue,
					feedback,
					similarityScore,
					representative,
					linkSource
			));
			priorityScoringService.recalculate(organizationId, issueId);
			return link;
		} catch (IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	@Transactional
	public IssueFeedback changeRepresentative(
			Long organizationId,
			Long feedbackId,
			Long issueId,
			boolean representative
	) {
		IssueFeedback link = getLinkForUpdate(organizationId, feedbackId, issueId);
		link.changeRepresentative(representative);
		return link;
	}

	@Transactional
	public void unlink(Long organizationId, Long feedbackId, Long issueId) {
		IssueFeedback link = getLinkForUpdate(organizationId, feedbackId, issueId);
		Issue issue = link.getIssue();
		issueFeedbackRepository.delete(link);
		// Aggregate queries must see the remaining links in the same transaction.
		issueFeedbackRepository.flush();
		IssueFeedbackRepository.FeedbackPeriod period = issueFeedbackRepository.getFeedbackPeriod(
				issueId, organizationId
		);
		issue.updateFeedbackPeriod(period.getFirstSeenAt(), period.getLastSeenAt());
		issue.clearCalculatedPriority();
		priorityScoringService.recalculate(organizationId, issueId);
	}

	private IssueFeedback getLinkForUpdate(Long organizationId, Long feedbackId, Long issueId) {
		feedbackRepository.findByIdAndOrganizationIdForUpdate(feedbackId, organizationId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		issueRepository.findByIdAndOrganizationIdForUpdate(issueId, organizationId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		return issueFeedbackRepository.findByIssueIdAndFeedbackId(issueId, feedbackId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
	}
}
