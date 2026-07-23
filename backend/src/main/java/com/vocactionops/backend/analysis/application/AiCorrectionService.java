package com.vocactionops.backend.analysis.application;

import com.vocactionops.backend.analysis.domain.AiCorrection;
import com.vocactionops.backend.analysis.domain.AiCorrectionField;
import com.vocactionops.backend.analysis.domain.AnalysisStatus;
import com.vocactionops.backend.analysis.domain.FeedbackAnalysis;
import com.vocactionops.backend.analysis.domain.Sentiment;
import com.vocactionops.backend.analysis.repository.AiCorrectionRepository;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.common.response.PageResponse;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.issue.application.IssuePriorityScoringService;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

import static com.vocactionops.backend.common.web.PageRequestFactory.newestFirst;

@Service
@PreAuthorize("hasAnyRole('ADMIN', 'PM', 'CS')")
public class AiCorrectionService {

	private final FeedbackRepository feedbackRepository;
	private final FeedbackAnalysisRepository analysisRepository;
	private final AiCorrectionRepository correctionRepository;
	private final UserRepository userRepository;
	private final IssuePriorityScoringService priorityScoringService;

	public AiCorrectionService(
			FeedbackRepository feedbackRepository,
			FeedbackAnalysisRepository analysisRepository,
			AiCorrectionRepository correctionRepository,
			UserRepository userRepository,
			IssuePriorityScoringService priorityScoringService
	) {
		this.feedbackRepository = feedbackRepository;
		this.analysisRepository = analysisRepository;
		this.correctionRepository = correctionRepository;
		this.userRepository = userRepository;
		this.priorityScoringService = priorityScoringService;
	}

	@Transactional
	public FeedbackAnalysisView correct(
			AuthenticatedUser authenticatedUser,
			Long feedbackId,
			String fieldName,
			String correctedValue,
			String reason
	) {
		Feedback feedback = getFeedback(authenticatedUser, feedbackId);
		FeedbackAnalysis analysis = analysisRepository.findByFeedbackIdAndFeedbackOrganizationId(
					feedbackId,
					authenticatedUser.organizationId()
				)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		User correctedBy = userRepository.findByIdAndOrganizationId(
					authenticatedUser.userId(),
					authenticatedUser.organizationId()
				)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		AiCorrectionField correctionField = AiCorrectionField.fromExternalName(
				fieldName == null ? null : fieldName.trim()
		);

		try {
			if (analysis.getStatus() != AnalysisStatus.SUCCESS) {
				throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
			}
			String previousValue = currentValue(analysis, correctionField);
			applyCorrection(analysis, correctionField, correctedValue);
			String normalizedValue = currentValue(analysis, correctionField);
			if (previousValue.equals(normalizedValue)) {
				throw new CustomException(ErrorCode.INVALID_REQUEST);
			}
			correctionRepository.save(new AiCorrection(
					feedback,
					correctionField,
					previousValue,
					normalizedValue,
					reason,
					correctedBy
			));
			if (correctionField == AiCorrectionField.SENTIMENT
					|| correctionField == AiCorrectionField.URGENCY_SCORE) {
				priorityScoringService.recalculateByFeedback(
						authenticatedUser.organizationId(),
						feedbackId
				);
			}
		} catch (CustomException exception) {
			throw exception;
		} catch (IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		} catch (IllegalStateException exception) {
			throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
		}
		return FeedbackAnalysisView.from(analysis);
	}

	@Transactional(readOnly = true)
	public PageResponse<CorrectionView> getCorrections(
			AuthenticatedUser authenticatedUser,
			Long feedbackId,
			int page,
			int size
	) {
		getFeedback(authenticatedUser, feedbackId);
		return PageResponse.from(correctionRepository.findPageByFeedbackAndOrganization(
				feedbackId,
				authenticatedUser.organizationId(),
				newestFirst(page, size, "createdAt")
		).map(CorrectionView::from));
	}

	private Feedback getFeedback(AuthenticatedUser authenticatedUser, Long feedbackId) {
		return feedbackRepository.findByIdAndOrganizationId(
					feedbackId,
					authenticatedUser.organizationId()
				)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
	}

	private String currentValue(FeedbackAnalysis analysis, AiCorrectionField field) {
		return switch (field) {
			case SENTIMENT -> analysis.getSentiment().name();
			case CATEGORY -> analysis.getCategory();
			case URGENCY_SCORE -> analysis.getUrgencyScore().stripTrailingZeros().toPlainString();
		};
	}

	private void applyCorrection(
			FeedbackAnalysis analysis,
			AiCorrectionField field,
			String correctedValue
	) {
		switch (field) {
			case SENTIMENT -> analysis.correctSentiment(Sentiment.valueOf(
					correctedValue.trim().toUpperCase(Locale.ROOT)
			));
			case CATEGORY -> analysis.correctCategory(correctedValue);
			case URGENCY_SCORE -> analysis.correctUrgencyScore(new BigDecimal(correctedValue.trim()));
		}
	}

	public record CorrectionView(
			Long id,
			String fieldName,
			String aiValue,
			String correctedValue,
			String reason,
			Long correctedBy,
			LocalDateTime createdAt
	) {
		private static CorrectionView from(AiCorrection correction) {
			return new CorrectionView(
					correction.getId(),
					correction.getFieldName().externalName(),
					correction.getPreviousValue(),
					correction.getCorrectedValue(),
					correction.getReason(),
					correction.getCorrectedBy().getId(),
					correction.getCreatedAt()
			);
		}
	}
}
