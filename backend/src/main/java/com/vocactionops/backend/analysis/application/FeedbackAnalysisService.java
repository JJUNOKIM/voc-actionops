package com.vocactionops.backend.analysis.application;

import com.vocactionops.backend.analysis.domain.FeedbackAnalysis;
import com.vocactionops.backend.analysis.domain.Sentiment;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.issue.application.IssuePriorityScoringService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class FeedbackAnalysisService {

	private final FeedbackRepository feedbackRepository;
	private final FeedbackAnalysisRepository analysisRepository;
	private final IssuePriorityScoringService priorityScoringService;

	public FeedbackAnalysisService(
			FeedbackRepository feedbackRepository,
			FeedbackAnalysisRepository analysisRepository,
			IssuePriorityScoringService priorityScoringService
	) {
		this.feedbackRepository = feedbackRepository;
		this.analysisRepository = analysisRepository;
		this.priorityScoringService = priorityScoringService;
	}

	@Transactional
	public FeedbackAnalysisView startAnalysis(
			Long organizationId,
			Long feedbackId,
			String modelName
	) {
		Feedback feedback = getFeedback(organizationId, feedbackId);
		FeedbackAnalysis analysis = analysisRepository
				.findByFeedbackIdAndFeedbackOrganizationId(feedbackId, organizationId)
				.orElse(null);

		try {
			if (analysis == null) {
				analysis = new FeedbackAnalysis(feedback, modelName);
				analysisRepository.save(analysis);
			} else {
				analysis.restart(modelName);
			}
		} catch (IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		} catch (IllegalStateException exception) {
			throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
		}
		return FeedbackAnalysisView.from(analysis);
	}

	@Transactional
	public FeedbackAnalysisView completeAnalysis(
			Long organizationId,
			Long feedbackId,
			AnalysisResult result
	) {
		if (result == null) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		FeedbackAnalysis analysis = getAnalysisEntity(organizationId, feedbackId);
		try {
			if (result.modelName() == null) {
				analysis.complete(
						result.sentiment(),
						result.sentimentScore(),
						result.category(),
						result.urgencyScore(),
						result.summary(),
						result.confidenceScore()
				);
			} else {
				analysis.complete(
						result.sentiment(),
						result.sentimentScore(),
						result.category(),
						result.urgencyScore(),
						result.summary(),
						result.confidenceScore(),
						result.modelName()
				);
			}
		} catch (IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		} catch (IllegalStateException exception) {
			throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
		}
		priorityScoringService.recalculateByFeedback(organizationId, feedbackId);
		return FeedbackAnalysisView.from(analysis);
	}

	@Transactional
	public FeedbackAnalysisView failAnalysis(
			Long organizationId,
			Long feedbackId,
			String errorMessage
	) {
		FeedbackAnalysis analysis = getAnalysisEntity(organizationId, feedbackId);
		try {
			analysis.fail(errorMessage);
		} catch (IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		} catch (IllegalStateException exception) {
			throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
		}
		return FeedbackAnalysisView.from(analysis);
	}

	@Transactional(readOnly = true)
	public FeedbackAnalysisView getAnalysis(Long organizationId, Long feedbackId) {
		return FeedbackAnalysisView.from(getAnalysisEntity(organizationId, feedbackId));
	}

	private Feedback getFeedback(Long organizationId, Long feedbackId) {
		return feedbackRepository.findByIdAndOrganizationId(feedbackId, organizationId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
	}

	private FeedbackAnalysis getAnalysisEntity(Long organizationId, Long feedbackId) {
		return analysisRepository.findByFeedbackIdAndFeedbackOrganizationId(feedbackId, organizationId)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
	}

	public record AnalysisResult(
			Sentiment sentiment,
			BigDecimal sentimentScore,
			String category,
			BigDecimal urgencyScore,
			String summary,
			BigDecimal confidenceScore,
			String modelName
	) {
		public AnalysisResult(
				Sentiment sentiment,
				BigDecimal sentimentScore,
				String category,
				BigDecimal urgencyScore,
				String summary,
				BigDecimal confidenceScore
		) {
			this(
					sentiment,
					sentimentScore,
					category,
					urgencyScore,
					summary,
					confidenceScore,
					null
			);
		}
	}
}
