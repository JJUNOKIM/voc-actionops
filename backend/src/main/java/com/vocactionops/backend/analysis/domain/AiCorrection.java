package com.vocactionops.backend.analysis.domain;

import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.user.domain.User;
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

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "ai_corrections")
public class AiCorrection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "feedback_id", nullable = false)
	private Feedback feedback;

	@Enumerated(EnumType.STRING)
	@Column(name = "field_name", nullable = false, length = 50)
	private AiCorrectionField fieldName;

	@Column(name = "ai_value", nullable = false, length = 1000)
	private String previousValue;

	@Column(name = "corrected_value", nullable = false, length = 1000)
	private String correctedValue;

	@Column(nullable = false, length = 500)
	private String reason;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "corrected_by", nullable = false)
	private User correctedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected AiCorrection() {
	}

	public AiCorrection(
			Feedback feedback,
			AiCorrectionField fieldName,
			String previousValue,
			String correctedValue,
			String reason,
			User correctedBy
	) {
		this.feedback = Objects.requireNonNull(feedback, "feedback must not be null");
		this.fieldName = Objects.requireNonNull(fieldName, "fieldName must not be null");
		this.previousValue = requireText(previousValue, "previousValue", 1000);
		this.correctedValue = requireText(correctedValue, "correctedValue", 1000);
		this.reason = requireText(reason, "reason", 500);
		this.correctedBy = Objects.requireNonNull(correctedBy, "correctedBy must not be null");
		validateOrganization(feedback, correctedBy);
	}

	@PrePersist
	private void onCreate() {
		createdAt = LocalDateTime.now();
	}

	private static void validateOrganization(Feedback feedback, User user) {
		Organization feedbackOrganization = feedback.getOrganization();
		Organization userOrganization = user.getOrganization();
		if (feedbackOrganization == userOrganization) {
			return;
		}
		if (feedbackOrganization.getId() == null
				|| !feedbackOrganization.getId().equals(userOrganization.getId())) {
			throw new IllegalArgumentException("correctedBy must belong to the feedback organization");
		}
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

	public AiCorrectionField getFieldName() {
		return fieldName;
	}

	public String getPreviousValue() {
		return previousValue;
	}

	public String getCorrectedValue() {
		return correctedValue;
	}

	public String getReason() {
		return reason;
	}

	public User getCorrectedBy() {
		return correctedBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
