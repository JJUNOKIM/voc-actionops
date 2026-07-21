package com.vocactionops.backend.action.domain;

import com.vocactionops.backend.common.entity.BaseTimeEntity;
import com.vocactionops.backend.issue.domain.Issue;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "actions")
public class Action extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "issue_id", nullable = false)
	private Issue issue;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(length = 1000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ActionStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assignee_id")
	private User assignee;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected Action() {
	}

	public Action(
			Issue issue,
			String title,
			String description,
			User assignee,
			LocalDate dueDate
	) {
		this.issue = Objects.requireNonNull(issue, "issue must not be null");
		this.title = requireText(title, "title", 150);
		this.description = normalizeNullable(description, "description", 1000);
		if (assignee != null) {
			validateOrganization(issue.getOrganization(), assignee.getOrganization());
		}
		this.assignee = assignee;
		this.dueDate = dueDate;
		this.status = ActionStatus.TODO;
	}

	public void changeStatus(ActionStatus target) {
		ActionStatus nextStatus = Objects.requireNonNull(target, "target status must not be null");
		if (!status.canTransitionTo(nextStatus)) {
			throw new IllegalStateException("action status transition is not allowed");
		}
		status = nextStatus;
		completedAt = nextStatus == ActionStatus.DONE ? LocalDateTime.now() : null;
	}

	private static void validateOrganization(Organization expected, Organization actual) {
		if (expected == actual) {
			return;
		}
		if (expected.getId() == null || !expected.getId().equals(actual.getId())) {
			throw new IllegalArgumentException("assignee must belong to the issue organization");
		}
	}

	private static String requireText(String value, String fieldName, int maxLength) {
		if (value == null || value.isBlank() || value.trim().length() > maxLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return value.trim();
	}

	private static String normalizeNullable(String value, String fieldName, int maxLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return requireText(value, fieldName, maxLength);
	}

	public Long getId() {
		return id;
	}

	public Issue getIssue() {
		return issue;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public ActionStatus getStatus() {
		return status;
	}

	public User getAssignee() {
		return assignee;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public LocalDateTime getCompletedAt() {
		return completedAt;
	}

	public long getVersion() {
		return version;
	}
}
