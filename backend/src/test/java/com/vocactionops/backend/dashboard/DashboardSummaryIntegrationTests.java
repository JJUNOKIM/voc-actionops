package com.vocactionops.backend.dashboard;

import com.vocactionops.backend.analysis.application.FeedbackAnalysisService;
import com.vocactionops.backend.analysis.application.FeedbackAnalysisService.AnalysisResult;
import com.vocactionops.backend.analysis.domain.Sentiment;
import com.vocactionops.backend.auth.token.JwtTokenProvider;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.issue.domain.Issue;
import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.domain.Priority;
import com.vocactionops.backend.issue.repository.IssueRepository;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
import com.vocactionops.backend.support.DatabaseCleaner;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardSummaryIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DatabaseCleaner databaseCleaner;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DatasetRepository datasetRepository;

	@Autowired
	private FeedbackRepository feedbackRepository;

	@Autowired
	private IssueRepository issueRepository;

	@Autowired
	private FeedbackAnalysisService analysisService;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Organization organization;
	private Organization otherOrganization;
	private User pm;
	private User viewer;
	private User developer;
	private User otherViewer;
	private Dataset dataset;
	private Dataset otherDataset;

	@BeforeEach
	void setUp() {
		databaseCleaner.clean();
		organization = organizationRepository.save(new Organization("VOC Team"));
		otherOrganization = organizationRepository.save(new Organization("Other Team"));
		pm = userRepository.save(user(organization, "pm@example.com", Role.PM));
		viewer = userRepository.save(user(organization, "viewer@example.com", Role.VIEWER));
		developer = userRepository.save(user(
				organization,
				"developer@example.com",
				Role.DEVELOPER
		));
		otherViewer = userRepository.save(user(
				otherOrganization,
				"other-viewer@example.com",
				Role.VIEWER
		));
		dataset = datasetRepository.save(new Dataset(
				organization,
				"App Reviews",
				SourceType.APP_REVIEW,
				pm
		));
		otherDataset = datasetRepository.save(new Dataset(
				otherOrganization,
				"Other Reviews",
				SourceType.APP_REVIEW,
				otherViewer
		));
	}

	@Test
	void summarizesMetricsWithinInclusiveDateRangeAndOrganization() throws Exception {
		Feedback negative = saveFeedback(
				organization,
				dataset,
				"negative",
				LocalDateTime.of(2026, 7, 10, 0, 0)
		);
		completeAnalysis(negative, Sentiment.NEGATIVE);
		Feedback positive = saveFeedback(
				organization,
				dataset,
				"positive",
				LocalDateTime.of(2026, 7, 12, 23, 59, 59)
		);
		completeAnalysis(positive, Sentiment.POSITIVE);
		saveFeedback(
				organization,
				dataset,
				"not-analyzed",
				LocalDateTime.of(2026, 7, 11, 8, 0)
		);
		Feedback ingestedInRange = saveFeedback(
				organization,
				dataset,
				"ingested-in-range",
				null
		);
		updateIngestedAt(ingestedInRange, LocalDateTime.of(2026, 7, 11, 9, 0));
		Feedback outsideRange = saveFeedback(
				organization,
				dataset,
				"outside",
				LocalDateTime.of(2026, 7, 13, 0, 0)
		);
		completeAnalysis(outsideRange, Sentiment.NEGATIVE);

		Feedback otherNegative = saveFeedback(
				otherOrganization,
				otherDataset,
				"other-negative",
				LocalDateTime.of(2026, 7, 11, 10, 0)
		);
		completeAnalysis(otherNegative, Sentiment.NEGATIVE);

		Issue openP0 = issueRepository.saveAndFlush(new Issue(
				organization,
				"Checkout unavailable",
				"Customers cannot complete checkout.",
				"PAYMENT",
				Priority.P0,
				pm
		));
		updateIssueTimes(
				openP0,
				LocalDateTime.of(2026, 7, 10, 0, 0),
				null
		);

		Issue resolvedP1 = resolvedIssue(organization, pm, Priority.P1, "Coupon failure");
		updateIssueTimes(
				resolvedP1,
				LocalDateTime.of(2026, 7, 11, 12, 0),
				LocalDateTime.of(2026, 7, 12, 12, 0)
		);

		Issue outsideP0 = issueRepository.saveAndFlush(new Issue(
				organization,
				"Outside issue",
				"Created outside the requested range.",
				"PAYMENT",
				Priority.P0,
				pm
		));
		updateIssueTimes(
				outsideP0,
				LocalDateTime.of(2026, 7, 13, 0, 0),
				null
		);

		Issue otherP1 = issueRepository.saveAndFlush(new Issue(
				otherOrganization,
				"Other issue",
				"Belongs to another organization.",
				"PAYMENT",
				Priority.P1,
				otherViewer
		));
		updateIssueTimes(
				otherP1,
				LocalDateTime.of(2026, 7, 11, 10, 0),
				null
		);

		mockMvc.perform(get("/api/v1/dashboard/summary")
						.queryParam("from", "2026-07-10")
						.queryParam("to", "2026-07-12")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalFeedbackCount").value(4))
				.andExpect(jsonPath("$.data.negativeFeedbackRate").value(50.0))
				.andExpect(jsonPath("$.data.newIssueCount").value(2))
				.andExpect(jsonPath("$.data.p0IssueCount").value(2))
				.andExpect(jsonPath("$.data.p1IssueCount").value(0))
				.andExpect(jsonPath("$.data.unresolvedIssueCount").value(2))
				.andExpect(jsonPath("$.data.averageResolutionHours").value(24.0));

		mockMvc.perform(get("/api/v1/dashboard/summary")
						.queryParam("from", "2026-07-10")
						.queryParam("to", "2026-07-12")
						.header("Authorization", bearer(otherViewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalFeedbackCount").value(1))
				.andExpect(jsonPath("$.data.negativeFeedbackRate").value(100.0))
				.andExpect(jsonPath("$.data.newIssueCount").value(1))
				.andExpect(jsonPath("$.data.p0IssueCount").value(0))
				.andExpect(jsonPath("$.data.p1IssueCount").value(1))
				.andExpect(jsonPath("$.data.unresolvedIssueCount").value(1))
				.andExpect(jsonPath("$.data.averageResolutionHours").doesNotExist());
	}

	@Test
	void returnsZeroMetricsAndNullAverageWhenNoDataExists() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/summary")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalFeedbackCount").value(0))
				.andExpect(jsonPath("$.data.negativeFeedbackRate").value(0.0))
				.andExpect(jsonPath("$.data.newIssueCount").value(0))
				.andExpect(jsonPath("$.data.p0IssueCount").value(0))
				.andExpect(jsonPath("$.data.p1IssueCount").value(0))
				.andExpect(jsonPath("$.data.unresolvedIssueCount").value(0))
				.andExpect(jsonPath("$.data.averageResolutionHours").doesNotExist());
	}

	@Test
	void rejectsInvalidDateRangeAndUnauthenticatedRequest() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/summary")
						.queryParam("from", "2026-07-13")
						.queryParam("to", "2026-07-12")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()));

		mockMvc.perform(get("/api/v1/dashboard/summary"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/dashboard/summary")
						.header("Authorization", bearer(developer)))
				.andExpect(status().isForbidden());
	}

	private Feedback saveFeedback(
			Organization feedbackOrganization,
			Dataset feedbackDataset,
			String externalId,
			LocalDateTime feedbackCreatedAt
	) {
		return feedbackRepository.save(new Feedback(
				feedbackOrganization,
				feedbackDataset,
				externalId,
				SourceType.APP_REVIEW,
				"new-customer",
				"mobile-app",
				new BigDecimal("1.0"),
				"Feedback content for " + externalId,
				"en",
				feedbackCreatedAt
		));
	}

	private void completeAnalysis(Feedback feedback, Sentiment sentiment) {
		analysisService.startAnalysis(
				feedback.getOrganization().getId(),
				feedback.getId(),
				"classifier-v1"
		);
		analysisService.completeAnalysis(
				feedback.getOrganization().getId(),
				feedback.getId(),
				new AnalysisResult(
						sentiment,
						sentiment == Sentiment.NEGATIVE
								? new BigDecimal("-0.80000")
								: new BigDecimal("0.80000"),
						"PAYMENT",
						new BigDecimal("0.7000"),
						"Payment feedback summary.",
						new BigDecimal("0.9000")
				)
		);
	}

	private Issue resolvedIssue(
			Organization issueOrganization,
			User assignee,
			Priority priority,
			String title
	) {
		Issue issue = issueRepository.saveAndFlush(new Issue(
				issueOrganization,
				title,
				"Resolved issue description.",
				"PAYMENT",
				priority,
				assignee
		));
		issue.changeStatus(IssueStatus.TRIAGED);
		issue.changeStatus(IssueStatus.ASSIGNED);
		issue.changeStatus(IssueStatus.IN_PROGRESS);
		issue.changeStatus(IssueStatus.RESOLVED);
		return issueRepository.saveAndFlush(issue);
	}

	private void updateIssueTimes(
			Issue issue,
			LocalDateTime createdAt,
			LocalDateTime resolvedAt
	) {
		jdbcTemplate.update(
				"UPDATE issues SET created_at = ?, updated_at = ?, resolved_at = ? WHERE id = ?",
				Timestamp.valueOf(createdAt),
				Timestamp.valueOf(resolvedAt == null ? createdAt : resolvedAt),
				resolvedAt == null ? null : Timestamp.valueOf(resolvedAt),
				issue.getId()
		);
	}

	private void updateIngestedAt(Feedback feedback, LocalDateTime ingestedAt) {
		jdbcTemplate.update(
				"UPDATE feedbacks SET ingested_at = ? WHERE id = ?",
				Timestamp.valueOf(ingestedAt),
				feedback.getId()
		);
	}

	private User user(Organization userOrganization, String email, Role role) {
		return new User(userOrganization, email, "encoded-password", email, role);
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenProvider.issue(user).value();
	}
}
