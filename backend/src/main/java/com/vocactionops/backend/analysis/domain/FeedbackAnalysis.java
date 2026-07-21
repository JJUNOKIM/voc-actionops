package com.vocactionops.backend.analysis.domain;

import com.vocactionops.backend.feedback.domain.Feedback;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "feedback_analysis")
public class FeedbackAnalysis {

	private static final BigDecimal MIN_SENTIMENT_SCORE = BigDecimal.valueOf(-1);
	private static final BigDecimal MAX_SCORE = BigDecimal.ONE;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "feedback_id", nullable = false, unique = true)
	private Feedback feedback;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private Sentiment sentiment;

	@Column(name = "sentiment_score", precision = 6, scale = 5)
	private BigDecimal sentimentScore;

	@Column(length = 100)
	private String category;

	@Column(name = "urgency_score", precision = 5, scale = 4)
	private BigDecimal urgencyScore;

	@Column(length = 1000)
	private String summary;

	@Column(name = "confidence_score", precision = 5, scale = 4)
	private BigDecimal confidenceScore;

	@Column(name = "model_name", nullable = false, length = 100)
	private String modelName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AnalysisStatus status;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	@Column(name = "analyzed_at")
	private LocalDateTime analyzedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected FeedbackAnalysis() {
	}

	public FeedbackAnalysis(Feedback feedback, String modelName) {
		this.feedback = Objects.requireNonNull(feedback, "feedback must not be null");
		this.modelName = requireText(modelName, "modelName", 100);
		this.status = AnalysisStatus.PENDING;
	}

	public void restart(String modelName) {
		if (status != AnalysisStatus.FAILED) {
			throw new IllegalStateException("only failed analysis can be restarted");
		}
		this.modelName = requireText(modelName, "modelName", 100);
		clearResult();
		status = AnalysisStatus.PENDING;
	}

	public void complete(
			Sentiment sentiment,
			BigDecimal sentimentScore,
			String category,
			BigDecimal urgencyScore,
			String summary,
			BigDecimal confidenceScore
	) {
		complete(
				sentiment,
				sentimentScore,
				category,
				urgencyScore,
				summary,
				confidenceScore,
				modelName
		);
	}

	public void complete(
			Sentiment sentiment,
			BigDecimal sentimentScore,
			String category,
			BigDecimal urgencyScore,
			String summary,
			BigDecimal confidenceScore,
			String modelName
	) {
		requirePending();
		this.sentiment = Objects.requireNonNull(sentiment, "sentiment must not be null");
		this.sentimentScore = requireScore(
				sentimentScore,
				MIN_SENTIMENT_SCORE,
				MAX_SCORE,
				5,
				"sentimentScore"
		);
		this.category = requireText(category, "category", 100);
		this.urgencyScore = requireScore(
				urgencyScore,
				BigDecimal.ZERO,
				MAX_SCORE,
				4,
				"urgencyScore"
		);
		this.summary = requireText(summary, "summary", 1000);
		this.confidenceScore = requireScore(
				confidenceScore,
				BigDecimal.ZERO,
				MAX_SCORE,
				4,
				"confidenceScore"
		);
		this.modelName = requireText(modelName, "modelName", 100);
		this.errorMessage = null;
		this.analyzedAt = LocalDateTime.now();
		this.status = AnalysisStatus.SUCCESS;
	}

	public void fail(String errorMessage) {
		requirePending();
		clearResult();
		this.errorMessage = requireText(errorMessage, "errorMessage", 1000);
		this.analyzedAt = LocalDateTime.now();
		this.status = AnalysisStatus.FAILED;
	}

	public void correctSentiment(Sentiment sentiment) {
		requireSuccess();
		this.sentiment = Objects.requireNonNull(sentiment, "sentiment must not be null");
	}

	public void correctCategory(String category) {
		requireSuccess();
		this.category = requireText(category, "category", 100);
	}

	public void correctUrgencyScore(BigDecimal urgencyScore) {
		requireSuccess();
		this.urgencyScore = requireScore(
				urgencyScore,
				BigDecimal.ZERO,
				MAX_SCORE,
				4,
				"urgencyScore"
		);
	}

	private void requirePending() {
		if (status != AnalysisStatus.PENDING) {
			throw new IllegalStateException("analysis must be pending");
		}
	}

	private void requireSuccess() {
		if (status != AnalysisStatus.SUCCESS) {
			throw new IllegalStateException("analysis must be successful");
		}
	}

	private void clearResult() {
		sentiment = null;
		sentimentScore = null;
		category = null;
		urgencyScore = null;
		summary = null;
		confidenceScore = null;
		errorMessage = null;
		analyzedAt = null;
	}

	private static BigDecimal requireScore(
			BigDecimal value,
			BigDecimal minimum,
			BigDecimal maximum,
			int maximumScale,
			String fieldName
	) {
		if (value == null
				|| value.compareTo(minimum) < 0
				|| value.compareTo(maximum) > 0
				|| value.stripTrailingZeros().scale() > maximumScale) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return value;
	}

	private static String requireText(String value, String fieldName, int maxLength) {
		if (value == null || value.isBlank() || value.trim().length() > maxLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return value.trim();
	}

	public Long getId() {
		return id;
	}

	public Feedback getFeedback() {
		return feedback;
	}

	public Sentiment getSentiment() {
		return sentiment;
	}

	public BigDecimal getSentimentScore() {
		return sentimentScore;
	}

	public String getCategory() {
		return category;
	}

	public BigDecimal getUrgencyScore() {
		return urgencyScore;
	}

	public String getSummary() {
		return summary;
	}

	public BigDecimal getConfidenceScore() {
		return confidenceScore;
	}

	public String getModelName() {
		return modelName;
	}

	public AnalysisStatus getStatus() {
		return status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public LocalDateTime getAnalyzedAt() {
		return analyzedAt;
	}

	public long getVersion() {
		return version;
	}
}
