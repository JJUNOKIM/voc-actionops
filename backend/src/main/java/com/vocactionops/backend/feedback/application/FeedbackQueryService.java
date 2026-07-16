package com.vocactionops.backend.feedback.application;

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

import static com.vocactionops.backend.common.web.PageRequestFactory.newestFirst;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class FeedbackQueryService {

	private final FeedbackRepository feedbackRepository;

	public FeedbackQueryService(FeedbackRepository feedbackRepository) {
		this.feedbackRepository = feedbackRepository;
	}

	public PageResponse<FeedbackView> getFeedbacks(
			AuthenticatedUser authenticatedUser,
			Long datasetId,
			SourceType sourceType,
			int page,
			int size
	) {
		return PageResponse.from(feedbackRepository.findPageByOrganization(
				authenticatedUser.organizationId(),
				datasetId,
				sourceType,
				newestFirst(page, size, "ingestedAt")
		).map(FeedbackView::from));
	}

	public FeedbackView getFeedback(AuthenticatedUser authenticatedUser, Long feedbackId) {
		Feedback feedback = feedbackRepository.findByIdAndOrganizationId(
						feedbackId,
						authenticatedUser.organizationId()
				)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		return FeedbackView.from(feedback);
	}

	public record FeedbackView(
			Long id,
			Long datasetId,
			String externalId,
			SourceType sourceType,
			String customerSegment,
			String productName,
			BigDecimal rating,
			String content,
			String language,
			LocalDateTime feedbackCreatedAt,
			LocalDateTime ingestedAt
	) {
		private static FeedbackView from(Feedback feedback) {
			return new FeedbackView(
					feedback.getId(),
					feedback.getDataset().getId(),
					feedback.getExternalId(),
					feedback.getSourceType(),
					feedback.getCustomerSegment(),
					feedback.getProductName(),
					feedback.getRating(),
					feedback.getContent(),
					feedback.getLanguage(),
					feedback.getFeedbackCreatedAt(),
					feedback.getIngestedAt()
			);
		}
	}
}
