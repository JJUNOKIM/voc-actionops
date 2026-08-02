package com.vocactionops.backend.issue;

import com.vocactionops.backend.issue.domain.Issue;
import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.domain.Priority;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IssueResolutionTimestampTests {

	@Test
	void recordsResolutionTimeAndClearsItWhenIssueReopens() {
		Organization organization = new Organization("VOC Team");
		User assignee = new User(
				organization,
				"pm@example.com",
				"encoded-password",
				"PM",
				Role.PM
		);
		Issue issue = new Issue(
				organization,
				"Checkout failure",
				"Customers cannot complete checkout.",
				"PAYMENT",
				Priority.P1,
				assignee
		);

		issue.changeStatus(IssueStatus.TRIAGED);
		issue.changeStatus(IssueStatus.ASSIGNED);
		issue.changeStatus(IssueStatus.IN_PROGRESS);
		issue.changeStatus(IssueStatus.RESOLVED);
		LocalDateTime firstResolution = issue.getResolvedAt();

		assertThat(firstResolution).isNotNull();
		issue.changeStatus(IssueStatus.MONITORING);
		assertThat(issue.getResolvedAt()).isEqualTo(firstResolution);

		issue.changeStatus(IssueStatus.IN_PROGRESS);
		assertThat(issue.getResolvedAt()).isNull();

		issue.changeStatus(IssueStatus.RESOLVED);
		assertThat(issue.getResolvedAt()).isNotNull();
	}
}
