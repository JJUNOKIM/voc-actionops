package com.vocactionops.backend.analysis.application;

import com.vocactionops.backend.analysis.domain.AnalysisStatus;
import com.vocactionops.backend.analysis.domain.FeedbackAnalysis;
import com.vocactionops.backend.analysis.domain.Sentiment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FeedbackAnalysisView(
		Long id,
		Sentiment sentiment,
		BigDecimal sentimentScore,
		String category,
		BigDecimal urgencyScore,
		String summary,
		BigDecimal confidenceScore,
		String modelName,
		AnalysisStatus status,
		String errorMessage,
		LocalDateTime analyzedAt
) {

	public static FeedbackAnalysisView from(FeedbackAnalysis analysis) {
		return new FeedbackAnalysisView(
				analysis.getId(),
				analysis.getSentiment(),
				analysis.getSentimentScore(),
				analysis.getCategory(),
				analysis.getUrgencyScore(),
				analysis.getSummary(),
				analysis.getConfidenceScore(),
				analysis.getModelName(),
				analysis.getStatus(),
				analysis.getErrorMessage(),
				analysis.getAnalyzedAt()
		);
	}
}
