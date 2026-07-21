package com.vocactionops.backend.issue.domain;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "issue_feedbacks")
public class IssueFeedback {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "issue_id", nullable = false)
	private Issue issue;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "feedback_id", nullable = false)
	private Feedback feedback;

	@Column(name = "similarity_score", precision = 5, scale = 4)
	private BigDecimal similarityScore;

	@Column(name = "is_representative", nullable = false)
	private boolean representative;

	@Enumerated(EnumType.STRING)
	@Column(name = "linked_by", nullable = false, length = 20)
	private LinkSource linkedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected IssueFeedback() {
	}

	public IssueFeedback(
			Issue issue,
			Feedback feedback,
			BigDecimal similarityScore,
			boolean representative,
			LinkSource linkedBy
	) {
		this.issue = Objects.requireNonNull(issue, "issue must not be null");
		this.feedback = Objects.requireNonNull(feedback, "feedback must not be null");
		this.similarityScore = validateSimilarityScore(similarityScore);
		this.representative = representative;
		this.linkedBy = Objects.requireNonNull(linkedBy, "linkedBy must not be null");
		issue.registerFeedback(feedback);
	}

	@PrePersist
	private void onCreate() {
		createdAt = LocalDateTime.now();
	}

	private static BigDecimal validateSimilarityScore(BigDecimal score) {
		if (score != null && (score.compareTo(BigDecimal.ZERO) < 0
				|| score.compareTo(BigDecimal.ONE) > 0
				|| score.stripTrailingZeros().scale() > 4)) {
			throw new IllegalArgumentException("similarityScore is invalid");
		}
		return score;
	}

	public Long getId() {
		return id;
	}

	public Issue getIssue() {
		return issue;
	}

	public Feedback getFeedback() {
		return feedback;
	}

	public BigDecimal getSimilarityScore() {
		return similarityScore;
	}

	public boolean isRepresentative() {
		return representative;
	}

	public LinkSource getLinkedBy() {
		return linkedBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
