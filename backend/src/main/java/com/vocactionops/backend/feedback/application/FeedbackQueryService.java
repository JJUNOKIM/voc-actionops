package com.vocactionops.backend.feedback.application;

import com.vocactionops.backend.analysis.application.FeedbackAnalysisView;
import com.vocactionops.backend.analysis.domain.AnalysisStatus;
import com.vocactionops.backend.analysis.domain.FeedbackAnalysis;
import com.vocactionops.backend.analysis.domain.Sentiment;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.common.response.PageResponse;
import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.vocactionops.backend.common.web.PageRequestFactory.newestFirst;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class FeedbackQueryService {

	private final FeedbackRepository feedbackRepository;
	private final FeedbackAnalysisRepository analysisRepository;

	public FeedbackQueryService(
			FeedbackRepository feedbackRepository,
			FeedbackAnalysisRepository analysisRepository
	) {
		this.feedbackRepository = feedbackRepository;
		this.analysisRepository = analysisRepository;
	}

	public PageResponse<FeedbackView> getFeedbacks(
			AuthenticatedUser authenticatedUser,
			Long datasetId,
			SourceType sourceType,
			int page,
			int size
	) {
		var feedbackPage = feedbackRepository.findPageByOrganization(
				authenticatedUser.organizationId(),
				datasetId,
				sourceType,
				newestFirst(page, size, "ingestedAt")
		);
		List<Long> feedbackIds = feedbackPage.stream()
				.map(Feedback::getId)
				.toList();
		Map<Long, FeedbackAnalysis> analysesByFeedbackId = feedbackIds.isEmpty()
				? Map.of()
				: analysisRepository.findAllByFeedbackIdsAndOrganization(
						feedbackIds,
						authenticatedUser.organizationId()
				).stream().collect(Collectors.toMap(
						analysis -> analysis.getFeedback().getId(),
						Function.identity()
				));

		return PageResponse.from(feedbackPage.map(feedback -> FeedbackView.from(
				feedback,
				analysesByFeedbackId.get(feedback.getId())
		)));
	}

	public FeedbackDetail getFeedback(AuthenticatedUser authenticatedUser, Long feedbackId) {
		Feedback feedback = feedbackRepository.findByIdAndOrganizationId(
						feedbackId,
						authenticatedUser.organizationId()
				)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		FeedbackAnalysisView analysis = analysisRepository
				.findByFeedbackIdAndFeedbackOrganizationId(
						feedbackId,
						authenticatedUser.organizationId()
				)
				.map(FeedbackAnalysisView::from)
				.orElse(null);
		return FeedbackDetail.from(feedback, analysis);
	}

	public record FeedbackView(
			Long id,
			Long datasetId,
			String datasetName,
			String externalId,
			SourceType sourceType,
			String customerSegment,
			String productName,
			BigDecimal rating,
			String content,
			String language,
			LocalDateTime feedbackCreatedAt,
			LocalDateTime ingestedAt,
			FeedbackAnalysisSummary analysis
	) {
		private static FeedbackView from(Feedback feedback, FeedbackAnalysis analysis) {
			return new FeedbackView(
					feedback.getId(),
					feedback.getDataset().getId(),
					feedback.getDataset().getName(),
					feedback.getExternalId(),
					feedback.getSourceType(),
					feedback.getCustomerSegment(),
					feedback.getProductName(),
					feedback.getRating(),
					feedback.getContent(),
					feedback.getLanguage(),
					feedback.getFeedbackCreatedAt(),
					feedback.getIngestedAt(),
					FeedbackAnalysisSummary.from(analysis)
			);
		}
	}

	public record FeedbackAnalysisSummary(
			AnalysisStatus status,
			Sentiment sentiment,
			String category,
			BigDecimal urgencyScore,
			BigDecimal confidenceScore
	) {
		private static FeedbackAnalysisSummary from(FeedbackAnalysis analysis) {
			if (analysis == null) {
				return null;
			}
			return new FeedbackAnalysisSummary(
					analysis.getStatus(),
					analysis.getSentiment(),
					analysis.getCategory(),
					analysis.getUrgencyScore(),
					analysis.getConfidenceScore()
			);
		}
	}

	public record FeedbackDetail(
			Long id,
			Long datasetId,
			String datasetName,
			String externalId,
			SourceType sourceType,
			String customerSegment,
			String productName,
			BigDecimal rating,
			String content,
			String language,
			LocalDateTime feedbackCreatedAt,
			LocalDateTime ingestedAt,
			FeedbackAnalysisView analysis
	) {
		private static FeedbackDetail from(Feedback feedback, FeedbackAnalysisView analysis) {
			return new FeedbackDetail(
					feedback.getId(),
					feedback.getDataset().getId(),
					feedback.getDataset().getName(),
					feedback.getExternalId(),
					feedback.getSourceType(),
					feedback.getCustomerSegment(),
					feedback.getProductName(),
					feedback.getRating(),
					feedback.getContent(),
					feedback.getLanguage(),
					feedback.getFeedbackCreatedAt(),
					feedback.getIngestedAt(),
					analysis
			);
		}
	}
}
