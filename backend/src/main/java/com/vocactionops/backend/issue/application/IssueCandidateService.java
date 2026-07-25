package com.vocactionops.backend.issue.application;

import com.vocactionops.backend.analysis.domain.AnalysisStatus;
import com.vocactionops.backend.analysis.domain.FeedbackAnalysis;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.issue.application.IssueService.IssueFeedbackView;
import com.vocactionops.backend.issue.application.IssueSimilarityCalculator.Similarity;
import com.vocactionops.backend.issue.domain.Issue;
import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.domain.LinkSource;
import com.vocactionops.backend.issue.domain.Priority;
import com.vocactionops.backend.issue.repository.IssueRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class IssueCandidateService {

	private static final int MAX_EVALUATED_ISSUES = 100;
	private static final int MAX_RESULTS = 10;
	private static final BigDecimal MIN_RECOMMENDATION_SCORE = new BigDecimal("0.4500");

	private final FeedbackRepository feedbackRepository;
	private final FeedbackAnalysisRepository analysisRepository;
	private final IssueRepository issueRepository;
	private final IssueSimilarityCalculator similarityCalculator;
	private final IssueFeedbackLinkService linkService;

	public IssueCandidateService(
			FeedbackRepository feedbackRepository,
			FeedbackAnalysisRepository analysisRepository,
			IssueRepository issueRepository,
			IssueSimilarityCalculator similarityCalculator,
			IssueFeedbackLinkService linkService
	) {
		this.feedbackRepository = feedbackRepository;
		this.analysisRepository = analysisRepository;
		this.issueRepository = issueRepository;
		this.similarityCalculator = similarityCalculator;
		this.linkService = linkService;
	}

	public List<IssueCandidateView> getCandidates(
			AuthenticatedUser authenticatedUser,
			Long feedbackId,
			int limit
	) {
		if (limit < 1 || limit > MAX_RESULTS) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		return scoreCandidates(authenticatedUser.organizationId(), feedbackId).stream()
				.filter(IssueCandidateService::meetsRecommendationThreshold)
				.sorted(candidateOrder())
				.limit(limit)
				.map(IssueCandidateView::from)
				.toList();
	}

	@Transactional
	@PreAuthorize("hasAnyRole('ADMIN', 'PM', 'CS')")
	public IssueFeedbackView confirmCandidate(
			AuthenticatedUser authenticatedUser,
			Long feedbackId,
			Long issueId,
			boolean representative
	) {
		ScoredIssue candidate = scoreCandidates(authenticatedUser.organizationId(), feedbackId).stream()
				.filter(IssueCandidateService::meetsRecommendationThreshold)
				.filter(scoredIssue -> scoredIssue.issue().getId().equals(issueId))
				.findFirst()
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		return IssueFeedbackView.from(linkService.link(
				authenticatedUser.organizationId(),
				feedbackId,
				issueId,
				candidate.similarity().score(),
				representative,
				LinkSource.AI
		));
	}

	private List<ScoredIssue> scoreCandidates(Long organizationId, Long feedbackId) {
		Feedback feedback = feedbackRepository.findByIdAndOrganizationId(feedbackId, organizationId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		FeedbackAnalysis analysis = analysisRepository
				.findByFeedbackIdAndFeedbackOrganizationId(feedbackId, organizationId)
				.orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_NOT_READY));
		if (analysis.getStatus() != AnalysisStatus.SUCCESS) {
			throw new CustomException(ErrorCode.ANALYSIS_NOT_READY);
		}

		String feedbackText = analysis.getSummary() + " " + feedback.getContent();
		return issueRepository.findCandidateIssues(
				organizationId,
				feedbackId,
				analysis.getCategory(),
				PageRequest.of(0, MAX_EVALUATED_ISSUES)
		).stream()
				.map(issue -> new ScoredIssue(issue, similarityCalculator.calculate(
						analysis.getCategory(),
						feedbackText,
						issue.getCategory(),
						issue.getTitle() + " " + issue.getDescription()
				)))
				.toList();
	}

	private static boolean meetsRecommendationThreshold(ScoredIssue candidate) {
		return candidate.similarity().score().compareTo(MIN_RECOMMENDATION_SCORE) >= 0;
	}

	private static Comparator<ScoredIssue> candidateOrder() {
		return Comparator.comparing((ScoredIssue candidate) -> candidate.similarity().score())
				.reversed()
				.thenComparing(candidate -> candidate.issue().getId());
	}

	private record ScoredIssue(Issue issue, Similarity similarity) {
	}

	public record IssueCandidateView(
			Long issueId,
			String title,
			String category,
			Priority priority,
			IssueStatus status,
			BigDecimal similarityScore,
			MatchSignals matchSignals
	) {
		private static IssueCandidateView from(ScoredIssue candidate) {
			Issue issue = candidate.issue();
			Similarity similarity = candidate.similarity();
			return new IssueCandidateView(
					issue.getId(),
					issue.getTitle(),
					issue.getCategory(),
					issue.getPriority(),
					issue.getStatus(),
					similarity.score(),
					new MatchSignals(
							similarity.categoryMatched(),
							similarity.categoryScore(),
							similarity.characterSimilarity(),
							similarity.tokenSimilarity(),
							similarity.textSimilarity()
					)
			);
		}
	}

	public record MatchSignals(
			boolean categoryMatched,
			BigDecimal categoryScore,
			BigDecimal characterSimilarity,
			BigDecimal tokenSimilarity,
			BigDecimal textSimilarity
	) {
	}
}
