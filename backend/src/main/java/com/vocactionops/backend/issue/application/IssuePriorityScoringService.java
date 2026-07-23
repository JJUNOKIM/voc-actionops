package com.vocactionops.backend.issue.application;

import com.vocactionops.backend.issue.domain.Issue;
import com.vocactionops.backend.issue.repository.IssueFeedbackRepository;
import com.vocactionops.backend.issue.repository.IssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IssuePriorityScoringService {

	private final IssueRepository issueRepository;
	private final IssueFeedbackRepository issueFeedbackRepository;
	private final IssuePriorityCalculator calculator;

	public IssuePriorityScoringService(
			IssueRepository issueRepository,
			IssueFeedbackRepository issueFeedbackRepository,
			IssuePriorityCalculator calculator
	) {
		this.issueRepository = issueRepository;
		this.issueFeedbackRepository = issueFeedbackRepository;
		this.calculator = calculator;
	}

	@Transactional
	public void recalculate(Long organizationId, Long issueId) {
		recalculateOne(organizationId, issueId);
	}

	@Transactional
	public void recalculateByFeedback(Long organizationId, Long feedbackId) {
		List<Long> issueIds = issueFeedbackRepository.findIssueIdsByFeedbackAndOrganization(
				feedbackId,
				organizationId
		);
		issueIds.forEach(issueId -> recalculateOne(organizationId, issueId));
	}

	private void recalculateOne(Long organizationId, Long issueId) {
		Issue issue = issueRepository.findByIdAndOrganizationIdForUpdate(issueId, organizationId)
				.orElse(null);
		if (issue == null) {
			return;
		}
		calculator.calculate(issueFeedbackRepository.getPriorityMetrics(
				issueId,
				organizationId
		)).ifPresent(result -> issue.applyCalculatedPriority(
				result.score(),
				result.priority()
		));
	}
}
