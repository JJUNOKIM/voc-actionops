package com.vocactionops.backend.feedback.domain;

import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.organization.domain.Organization;
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
@Table(name = "feedbacks")
public class Feedback {

	private static final BigDecimal MIN_RATING = BigDecimal.ZERO;
	private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "dataset_id", nullable = false)
	private Dataset dataset;

	@Column(name = "external_id", length = 150)
	private String externalId;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 50)
	private SourceType sourceType;

	@Column(name = "customer_segment", length = 100)
	private String customerSegment;

	@Column(name = "product_name", length = 150)
	private String productName;

	@Column(precision = 3, scale = 1)
	private BigDecimal rating;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(length = 20)
	private String language;

	@Column(name = "feedback_created_at")
	private LocalDateTime feedbackCreatedAt;

	@Column(name = "ingested_at", nullable = false, updatable = false)
	private LocalDateTime ingestedAt;

	protected Feedback() {
	}

	public Feedback(
			Organization organization,
			Dataset dataset,
			String externalId,
			SourceType sourceType,
			String customerSegment,
			String productName,
			BigDecimal rating,
			String content,
			String language,
			LocalDateTime feedbackCreatedAt
	) {
		this.organization = Objects.requireNonNull(organization, "organization must not be null");
		this.dataset = Objects.requireNonNull(dataset, "dataset must not be null");
		validateOrganization(organization, dataset);
		this.externalId = normalizeNullable(externalId);
		this.sourceType = Objects.requireNonNull(sourceType, "sourceType must not be null");
		this.customerSegment = normalizeNullable(customerSegment);
		this.productName = normalizeNullable(productName);
		this.rating = validateRating(rating);
		this.content = requireContent(content);
		this.language = normalizeNullable(language);
		this.feedbackCreatedAt = feedbackCreatedAt;
	}

	@PrePersist
	private void onCreate() {
		ingestedAt = LocalDateTime.now();
	}

	private static void validateOrganization(Organization organization, Dataset dataset) {
		Organization datasetOrganization = dataset.getOrganization();
		if (organization == datasetOrganization) {
			return;
		}
		if (organization.getId() == null
				|| !organization.getId().equals(datasetOrganization.getId())) {
			throw new IllegalArgumentException("dataset must belong to the feedback organization");
		}
	}

	private static BigDecimal validateRating(BigDecimal rating) {
		if (rating != null
				&& (rating.compareTo(MIN_RATING) < 0 || rating.compareTo(MAX_RATING) > 0)) {
			throw new IllegalArgumentException("rating must be between 0 and 5");
		}
		return rating;
	}

	private static String requireContent(String content) {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("content must not be blank");
		}
		return content;
	}

	private static String normalizeNullable(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public Long getId() {
		return id;
	}

	public Organization getOrganization() {
		return organization;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public String getExternalId() {
		return externalId;
	}

	public SourceType getSourceType() {
		return sourceType;
	}

	public String getCustomerSegment() {
		return customerSegment;
	}

	public String getProductName() {
		return productName;
	}

	public BigDecimal getRating() {
		return rating;
	}

	public String getContent() {
		return content;
	}

	public String getLanguage() {
		return language;
	}

	public LocalDateTime getFeedbackCreatedAt() {
		return feedbackCreatedAt;
	}

	public LocalDateTime getIngestedAt() {
		return ingestedAt;
	}
}
