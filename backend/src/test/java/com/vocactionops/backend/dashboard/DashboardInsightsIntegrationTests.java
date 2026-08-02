package com.vocactionops.backend.dashboard;

import com.vocactionops.backend.action.domain.Action;
import com.vocactionops.backend.action.domain.ActionStatus;
import com.vocactionops.backend.action.repository.ActionRepository;
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
import com.vocactionops.backend.issue.application.IssueFeedbackLinkService;
import com.vocactionops.backend.issue.domain.Issue;
import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.domain.LinkSource;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardInsightsIntegrationTests {

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
	private ActionRepository actionRepository;

	@Autowired
	private FeedbackAnalysisService analysisService;

	@Autowired
	private IssueFeedbackLinkService linkService;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private Organization organization;
	private Organization otherOrganization;
	private User pm;
	private User viewer;
	private User developer;
	private User otherViewer;
	private Dataset dataset;
	private Dataset otherDataset;
	private Issue highPriorityIssue;
	private Issue highVolumeIssue;
	private Issue manualIssue;
	private Issue secondManualIssue;
	private Issue otherIssue;

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

		Feedback urgentNegative = analyzedFeedback(
				organization,
				dataset,
				"urgent-negative",
				LocalDateTime.of(2026, 7, 10, 10, 0),
				Sentiment.NEGATIVE,
				"PAYMENT",
				new BigDecimal("1.0000")
		);
		Feedback volumeOne = analyzedFeedback(
				organization,
				dataset,
				"volume-1",
				LocalDateTime.of(2026, 7, 10, 11, 0),
				Sentiment.POSITIVE,
				"PAYMENT",
				new BigDecimal("0.1000")
		);
		Feedback volumeTwo = analyzedFeedback(
				organization,
				dataset,
				"volume-2",
				LocalDateTime.of(2026, 7, 11, 11, 0),
				Sentiment.POSITIVE,
				"PAYMENT",
				new BigDecimal("0.1000")
		);
		Feedback volumeThree = analyzedFeedback(
				organization,
				dataset,
				"volume-3",
				LocalDateTime.of(2026, 7, 12, 23, 59, 59),
				Sentiment.POSITIVE,
				"PAYMENT",
				new BigDecimal("0.1000")
		);
		Feedback deliveryNegative = analyzedFeedback(
				organization,
				dataset,
				"delivery-negative",
				LocalDateTime.of(2026, 7, 11, 14, 0),
				Sentiment.NEGATIVE,
				"DELIVERY",
				new BigDecimal("0.8000")
		);
		analyzedFeedback(
				organization,
				dataset,
				"outside-range",
				LocalDateTime.of(2026, 7, 13, 0, 0),
				Sentiment.NEGATIVE,
				"PAYMENT",
				new BigDecimal("0.9000")
		);
		saveFeedback(
				organization,
				dataset,
				"not-analyzed",
				LocalDateTime.of(2026, 7, 11, 15, 0)
		);
		Feedback otherNegative = analyzedFeedback(
				otherOrganization,
				otherDataset,
				"other-negative",
				LocalDateTime.of(2026, 7, 11, 16, 0),
				Sentiment.NEGATIVE,
				"PAYMENT",
				new BigDecimal("1.0000")
		);

		highPriorityIssue = saveIssue(organization, pm, "Urgent payment failure", "PAYMENT", Priority.P3);
		highVolumeIssue = saveIssue(organization, pm, "Repeated checkout friction", "payment", Priority.P3);
		manualIssue = saveIssue(organization, null, "Login issue", "login", Priority.P0);
		secondManualIssue = saveIssue(organization, null, "Account issue", "ACCOUNT", Priority.P0);
		Issue closedDeliveryIssue = saveIssue(
				organization,
				pm,
				"Resolved delivery delay",
				"DELIVERY",
				Priority.P3
		);
		otherIssue = saveIssue(
				otherOrganization,
				otherViewer,
				"Other payment issue",
				"PAYMENT",
				Priority.P3
		);

		link(organization, urgentNegative, highPriorityIssue);
		link(organization, volumeOne, highVolumeIssue);
		link(organization, volumeTwo, highVolumeIssue);
		link(organization, volumeThree, highVolumeIssue);
		link(organization, deliveryNegative, closedDeliveryIssue);
		link(otherOrganization, otherNegative, otherIssue);
		closeIssue(closedDeliveryIssue);

		actionRepository.save(new Action(
				highPriorityIssue,
				"Investigate payment gateway",
				null,
				pm,
				LocalDate.of(2026, 7, 15)
		));
		Action completedAction = new Action(
				highPriorityIssue,
				"Reproduce checkout failure",
				null,
				pm,
				LocalDate.of(2026, 7, 14)
		);
		completedAction.changeStatus(ActionStatus.IN_PROGRESS);
		completedAction.changeStatus(ActionStatus.DONE);
		actionRepository.save(completedAction);
		Action inProgressAction = new Action(
				highVolumeIssue,
				"Review checkout UX",
				null,
				pm,
				LocalDate.of(2026, 7, 20)
		);
		inProgressAction.changeStatus(ActionStatus.IN_PROGRESS);
		actionRepository.save(inProgressAction);
	}

	@Test
	void returnsPeriodFeedbackMetricsWithCurrentActiveIssueCounts() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/category-breakdown")
						.queryParam("from", "2026-07-10")
						.queryParam("to", "2026-07-12")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(4))
				.andExpect(jsonPath("$.data[0].category").value("PAYMENT"))
				.andExpect(jsonPath("$.data[0].issueCount").value(2))
				.andExpect(jsonPath("$.data[0].feedbackCount").value(4))
				.andExpect(jsonPath("$.data[0].negativeFeedbackRate").value(25.0))
				.andExpect(jsonPath("$.data[1].category").value("DELIVERY"))
				.andExpect(jsonPath("$.data[1].issueCount").value(0))
				.andExpect(jsonPath("$.data[1].feedbackCount").value(1))
				.andExpect(jsonPath("$.data[1].negativeFeedbackRate").value(100.0))
				.andExpect(jsonPath("$.data[2].category").value("ACCOUNT"))
				.andExpect(jsonPath("$.data[2].issueCount").value(1))
				.andExpect(jsonPath("$.data[2].feedbackCount").value(0))
				.andExpect(jsonPath("$.data[2].negativeFeedbackRate").value(0.0))
				.andExpect(jsonPath("$.data[3].category").value("LOGIN"));

		mockMvc.perform(get("/api/v1/dashboard/category-breakdown")
						.queryParam("from", "2026-07-10")
						.queryParam("to", "2026-07-12")
						.header("Authorization", bearer(otherViewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].category").value("PAYMENT"))
				.andExpect(jsonPath("$.data[0].issueCount").value(1))
				.andExpect(jsonPath("$.data[0].feedbackCount").value(1))
				.andExpect(jsonPath("$.data[0].negativeFeedbackRate").value(100.0));
	}

	@Test
	void ranksActiveIssuesBySupportedSortAndStableTieBreakers() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/top-issues")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(4))
				.andExpect(jsonPath("$.data[0].issueId").value(highPriorityIssue.getId()))
				.andExpect(jsonPath("$.data[0].priorityScore").value(71.5))
				.andExpect(jsonPath("$.data[0].feedbackCount").value(1))
				.andExpect(jsonPath("$.data[0].feedbackGrowthRate").doesNotExist())
				.andExpect(jsonPath("$.data[0].negativeFeedbackRate").value(100.0))
				.andExpect(jsonPath("$.data[0].unresolvedActionCount").value(1))
				.andExpect(jsonPath("$.data[0].assigneeId").value(pm.getId()))
				.andExpect(jsonPath("$.data[1].issueId").value(highVolumeIssue.getId()))
				.andExpect(jsonPath("$.data[1].priorityScore").value(8.0))
				.andExpect(jsonPath("$.data[1].feedbackCount").value(3))
				.andExpect(jsonPath("$.data[1].negativeFeedbackRate").value(0.0))
				.andExpect(jsonPath("$.data[1].unresolvedActionCount").value(1))
				.andExpect(jsonPath("$.data[2].issueId").value(manualIssue.getId()))
				.andExpect(jsonPath("$.data[2].priorityScore").doesNotExist())
				.andExpect(jsonPath("$.data[2].assigneeId").doesNotExist())
				.andExpect(jsonPath("$.data[3].issueId").value(secondManualIssue.getId()));

		mockMvc.perform(get("/api/v1/dashboard/top-issues")
						.queryParam("sortBy", "feedback_count")
						.queryParam("limit", "2")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[0].issueId").value(highVolumeIssue.getId()))
				.andExpect(jsonPath("$.data[1].issueId").value(highPriorityIssue.getId()));

		mockMvc.perform(get("/api/v1/dashboard/top-issues")
						.header("Authorization", bearer(otherViewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].issueId").value(otherIssue.getId()));
	}

	@Test
	void rejectsUnsupportedOptionsAndDeveloperAccess() throws Exception {
		mockMvc.perform(get("/api/v1/dashboard/category-breakdown")
						.queryParam("from", "2026-07-13")
						.queryParam("to", "2026-07-12")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()));

		mockMvc.perform(get("/api/v1/dashboard/top-issues")
						.queryParam("limit", "51")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()));

		mockMvc.perform(get("/api/v1/dashboard/category-breakdown")
						.header("Authorization", bearer(developer)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/dashboard/top-issues")
						.header("Authorization", bearer(developer)))
				.andExpect(status().isForbidden());
	}

	private Feedback analyzedFeedback(
			Organization feedbackOrganization,
			Dataset feedbackDataset,
			String externalId,
			LocalDateTime feedbackCreatedAt,
			Sentiment sentiment,
			String category,
			BigDecimal urgencyScore
	) {
		Feedback feedback = saveFeedback(
				feedbackOrganization,
				feedbackDataset,
				externalId,
				feedbackCreatedAt
		);
		analysisService.startAnalysis(
				feedbackOrganization.getId(),
				feedback.getId(),
				"classifier-v1"
		);
		analysisService.completeAnalysis(
				feedbackOrganization.getId(),
				feedback.getId(),
				new AnalysisResult(
						sentiment,
						sentiment == Sentiment.NEGATIVE
								? new BigDecimal("-0.80000")
								: new BigDecimal("0.80000"),
						category,
						urgencyScore,
						"Feedback summary for " + externalId,
						new BigDecimal("0.9000")
				)
		);
		return feedback;
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

	private Issue saveIssue(
			Organization issueOrganization,
			User assignee,
			String title,
			String category,
			Priority priority
	) {
		return issueRepository.saveAndFlush(new Issue(
				issueOrganization,
				title,
				"Issue description for " + title,
				category,
				priority,
				assignee
		));
	}

	private void link(Organization linkOrganization, Feedback feedback, Issue issue) {
		linkService.link(
				linkOrganization.getId(),
				feedback.getId(),
				issue.getId(),
				null,
				false,
				LinkSource.MANUAL
		);
	}

	private void closeIssue(Issue issue) {
		Issue target = issueRepository.findByIdAndOrganizationId(
				issue.getId(),
				issue.getOrganization().getId()
		).orElseThrow();
		target.changeStatus(IssueStatus.TRIAGED);
		target.changeStatus(IssueStatus.ASSIGNED);
		target.changeStatus(IssueStatus.IN_PROGRESS);
		target.changeStatus(IssueStatus.RESOLVED);
		target.changeStatus(IssueStatus.MONITORING);
		target.changeStatus(IssueStatus.CLOSED);
		issueRepository.saveAndFlush(target);
	}

	private User user(Organization userOrganization, String email, Role role) {
		return new User(userOrganization, email, "encoded-password", email, role);
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenProvider.issue(user).value();
	}
}
