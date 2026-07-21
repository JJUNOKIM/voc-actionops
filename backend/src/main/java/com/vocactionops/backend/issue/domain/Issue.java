package com.vocactionops.backend.issue.domain;

import com.vocactionops.backend.common.entity.BaseTimeEntity;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "issues")
public class Issue extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(nullable = false, length = 1000)
	private String description;

	@Column(nullable = false, length = 100)
	private String category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Priority priority;

	@Column(name = "priority_score", precision = 5, scale = 2)
	private BigDecimal priorityScore;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private IssueStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private User assignee;

	@Column(name = "first_seen_at")
	private LocalDateTime firstSeenAt;

	@Column(name = "last_seen_at")
	private LocalDateTime lastSeenAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected Issue() {
	}

	public Issue(
			Organization organization,
			String title,
			String description,
			String category,
			Priority priority,
			User assignee
	) {
		this.organization = Objects.requireNonNull(organization, "organization must not be null");
		this.title = requireText(title, "title", 150);
		this.description = requireText(description, "description", 1000);
		this.category = requireText(category, "category", 100);
		this.priority = Objects.requireNonNull(priority, "priority must not be null");
		if (assignee != null) {
			validateOrganization(organization, assignee);
		}
		this.assignee = assignee;
		this.status = IssueStatus.NEW;
	}

	public void assignTo(User assignee) {
		if (status == IssueStatus.CLOSED) {
			throw new IllegalStateException("closed issue cannot be reassigned");
		}
		User nextAssignee = Objects.requireNonNull(assignee, "assignee must not be null");
		validateOrganization(organization, nextAssignee);
		this.assignee = nextAssignee;
	}

	public void changeStatus(IssueStatus target) {
		IssueStatus nextStatus = Objects.requireNonNull(target, "target status must not be null");
		if (!status.canTransitionTo(nextStatus)) {
			throw new IllegalStateException("issue status transition is not allowed");
		}
		if (nextStatus.requiresAssignee() && assignee == null) {
			throw new IllegalStateException("assignee is required for target status");
		}
		status = nextStatus;
	}

	void registerFeedback(Feedback feedback) {
		Feedback linkedFeedback = Objects.requireNonNull(feedback, "feedback must not be null");
		validateOrganization(organization, linkedFeedback.getOrganization());
		LocalDateTime occurredAt = linkedFeedback.getFeedbackCreatedAt() == null
				? linkedFeedback.getIngestedAt()
				: linkedFeedback.getFeedbackCreatedAt();
		if (firstSeenAt == null || occurredAt.isBefore(firstSeenAt)) {
			firstSeenAt = occurredAt;
		}
		if (lastSeenAt == null || occurredAt.isAfter(lastSeenAt)) {
			lastSeenAt = occurredAt;
		}
	}

	private static void validateOrganization(Organization expected, User user) {
		validateOrganization(expected, user.getOrganization());
	}

	private static void validateOrganization(Organization expected, Organization actual) {
		if (expected == actual) {
			return;
		}
		if (expected.getId() == null || !expected.getId().equals(actual.getId())) {
			throw new IllegalArgumentException("resource must belong to the issue organization");
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

	public Organization getOrganization() {
		return organization;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getCategory() {
		return category;
	}

	public Priority getPriority() {
		return priority;
	}

	public BigDecimal getPriorityScore() {
		return priorityScore;
	}

	public IssueStatus getStatus() {
		return status;
	}

	public User getAssignee() {
		return assignee;
	}

	public LocalDateTime getFirstSeenAt() {
		return firstSeenAt;
	}

	public LocalDateTime getLastSeenAt() {
		return lastSeenAt;
	}

	public long getVersion() {
		return version;
	}
}
