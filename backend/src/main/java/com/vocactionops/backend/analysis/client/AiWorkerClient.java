package com.vocactionops.backend.analysis.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vocactionops.backend.analysis.domain.Sentiment;

import java.math.BigDecimal;

public interface AiWorkerClient {

	AnalysisResult analyze(AnalysisRequest request);

	record AnalysisRequest(
			@JsonProperty("feedback_id") Long feedbackId,
			String content,
			BigDecimal rating,
			String language,
			@JsonProperty("product_name") String productName,
			@JsonProperty("customer_segment") String customerSegment
	) {
		public AnalysisRequest {
			if (feedbackId == null || feedbackId < 1 || content == null || content.isBlank()) {
				throw new IllegalArgumentException("analysis request is invalid");
			}
		}
	}

	record AnalysisResult(
			@JsonProperty("feedback_id") Long feedbackId,
			Sentiment sentiment,
			@JsonProperty("sentiment_score") BigDecimal sentimentScore,
			String category,
			@JsonProperty("urgency_score") BigDecimal urgencyScore,
			String summary,
			@JsonProperty("confidence_score") BigDecimal confidenceScore,
			@JsonProperty("model_name") String modelName
	) {
		private static final BigDecimal MIN_SENTIMENT_SCORE = BigDecimal.valueOf(-1);
		private static final BigDecimal MAX_SCORE = BigDecimal.ONE;

		public AnalysisResult {
			if (feedbackId == null || feedbackId < 1 || sentiment == null
					|| !validScore(sentimentScore, MIN_SENTIMENT_SCORE, MAX_SCORE, 5)
					|| !validText(category, 100)
					|| !validScore(urgencyScore, BigDecimal.ZERO, MAX_SCORE, 4)
					|| !validText(summary, 1000)
					|| !validScore(confidenceScore, BigDecimal.ZERO, MAX_SCORE, 4)
					|| !validText(modelName, 100)) {
				throw new IllegalArgumentException("analysis response is invalid");
			}
		}

		private static boolean validScore(
				BigDecimal value,
				BigDecimal minimum,
				BigDecimal maximum,
				int maximumScale
		) {
			return value != null
					&& value.compareTo(minimum) >= 0
					&& value.compareTo(maximum) <= 0
					&& value.stripTrailingZeros().scale() <= maximumScale;
		}

		private static boolean validText(String value, int maxLength) {
			return value != null && !value.isBlank() && value.trim().length() <= maxLength;
		}
	}
}
