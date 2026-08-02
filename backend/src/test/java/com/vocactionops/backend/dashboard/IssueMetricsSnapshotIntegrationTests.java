package com.vocactionops.backend.dashboard;

import com.vocactionops.backend.action.domain.Action;
import com.vocactionops.backend.action.repository.ActionRepository;
import com.vocactionops.backend.analysis.application.FeedbackAnalysisService;
import com.vocactionops.backend.analysis.application.FeedbackAnalysisService.AnalysisResult;
import com.vocactionops.backend.analysis.domain.Sentiment;
import com.vocactionops.backend.auth.token.JwtTokenProvider;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dashboard.application.IssueMetricsSnapshotScheduler;
import com.vocactionops.backend.dashboard.application.IssueMetricsSnapshotService;
import com.vocactionops.backend.dashboard.domain.IssueMetricsSnapshot;
import com.vocactionops.backend.dashboard.repository.IssueMetricsSnapshotRepository;
import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.issue.application.IssueFeedbackLinkService;
import com.vocactionops.backend.issue.domain.Issue;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IssueMetricsSnapshotIntegrationTests {

	private static final LocalDate FIRST_DATE = LocalDate.of(2026, 7, 10);
	private static final LocalDate SECOND_DATE = LocalDate.of(2026, 7, 11);

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
	private IssueMetricsSnapshotService snapshotService;

	@Autowired
	private IssueMetricsSnapshotScheduler snapshotScheduler;

	@Autowired
	private IssueMetricsSnapshotRepository snapshotRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private Organization organization;
	private Organization otherOrganization;
	private User admin;
	private User pm;
	private User viewer;
	private User developer;
	private User otherPm;
	private Dataset dataset;

	@BeforeEach
	void setUp() {
		databaseCleaner.clean();
		organization = organizationRepository.save(new Organization("VOC Team"));
		otherOrganization = organizationRepository.save(new Organization("Other Team"));
		admin = userRepository.save(user(organization, "admin@example.com", Role.ADMIN));
		pm = userRepository.save(user(organization, "pm@example.com", Role.PM));
		viewer = userRepository.save(user(organization, "viewer@example.com", Role.VIEWER));
		developer = userRepository.save(user(
				organization,
				"developer@example.com",
				Role.DEVELOPER
		));
		otherPm = userRepository.save(user(
				otherOrganization,
				"other-pm@example.com",
				Role.PM
		));
		dataset = datasetRepository.save(new Dataset(
				organization,
				"App Reviews",
				SourceType.APP_REVIEW,
				pm
		));
	}

	@Test
	void refreshesSameDateAndReturnsChronologicalTrend() throws Exception {
		Issue issue = saveIssue(organization, pm, "Payment failure");
		link(organization, analyzedFeedback(
				organization,
				dataset,
				"first",
				Sentiment.NEGATIVE,
				new BigDecimal("-0.80000"),
				new BigDecimal("1.0000")
		), issue);
		snapshotService.captureOrganization(organization.getId(), FIRST_DATE);

		link(organization, analyzedFeedback(
				organization,
				dataset,
				"second",
				Sentiment.POSITIVE,
				new BigDecimal("0.40000"),
				new BigDecimal("0.2000")
		), issue);
		link(organization, feedback("not-analyzed", dataset), issue);
		actionRepository.save(new Action(
				issue,
				"Check payment gateway",
				null,
				pm,
				LocalDate.of(2026, 7, 15)
		));
		snapshotService.captureOrganization(organization.getId(), FIRST_DATE);

		List<IssueMetricsSnapshot> firstDay = snapshotRepository.findAllByOrganizationAndDate(
				organization.getId(),
				FIRST_DATE
		);
		assertThat(firstDay).hasSize(1);
		assertThat(firstDay.get(0).getFeedbackCount()).isEqualTo(3);
		assertThat(firstDay.get(0).getAnalyzedFeedbackCount()).isEqualTo(2);
		assertThat(firstDay.get(0).getNegativeFeedbackCount()).isEqualTo(1);
		assertThat(firstDay.get(0).getAverageSentimentScore())
				.isEqualByComparingTo("-0.20000");
		assertThat(firstDay.get(0).getUnresolvedActionCount()).isEqualTo(1);

		link(organization, analyzedFeedback(
				organization,
				dataset,
				"third",
				Sentiment.NEGATIVE,
				new BigDecimal("-0.80000"),
				new BigDecimal("0.8000")
		), issue);
		snapshotService.captureOrganization(organization.getId(), SECOND_DATE);

		mockMvc.perform(get("/api/v1/dashboard/issue-trends")
						.queryParam("issueId", issue.getId().toString())
						.queryParam("from", FIRST_DATE.toString())
						.queryParam("to", SECOND_DATE.toString())
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.issueId").value(issue.getId()))
				.andExpect(jsonPath("$.data.feedbackGrowthRate").value(33.33))
				.andExpect(jsonPath("$.data.points.length()").value(2))
				.andExpect(jsonPath("$.data.points[0].snapshotDate").value("2026-07-10"))
				.andExpect(jsonPath("$.data.points[0].feedbackCount").value(3))
				.andExpect(jsonPath("$.data.points[0].analyzedFeedbackCount").value(2))
				.andExpect(jsonPath("$.data.points[0].negativeFeedbackRate").value(50.0))
				.andExpect(jsonPath("$.data.points[0].averageSentimentScore").value(-0.2))
				.andExpect(jsonPath("$.data.points[1].snapshotDate").value("2026-07-11"))
				.andExpect(jsonPath("$.data.points[1].feedbackCount").value(4))
				.andExpect(jsonPath("$.data.points[1].analyzedFeedbackCount").value(3))
				.andExpect(jsonPath("$.data.points[1].negativeFeedbackRate").value(66.67));
	}

	@Test
	void refreshEndpointUsesCurrentOrganizationAndRequiresManagementRole() throws Exception {
		saveIssue(organization, pm, "Payment failure");
		saveIssue(otherOrganization, otherPm, "Other payment failure");

		mockMvc.perform(post("/api/v1/dashboard/snapshots/refresh")
						.header("Authorization", bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.issueCount").value(1));

		mockMvc.perform(post("/api/v1/dashboard/snapshots/refresh")
						.header("Authorization", bearer(pm)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.issueCount").value(1));

		LocalDate today = snapshotService.currentDate();
		assertThat(snapshotRepository.findAllByOrganizationAndDate(
				organization.getId(),
				today
		)).hasSize(1);
		assertThat(snapshotRepository.findAllByOrganizationAndDate(
				otherOrganization.getId(),
				today
		)).isEmpty();

		mockMvc.perform(post("/api/v1/dashboard/snapshots/refresh")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/dashboard/snapshots/refresh")
						.header("Authorization", bearer(developer)))
				.andExpect(status().isForbidden());
	}

	@Test
	void isolatesTrendByOrganizationAndValidatesDateRange() throws Exception {
		Issue issue = saveIssue(organization, pm, "Payment failure");
		snapshotService.captureOrganization(organization.getId(), FIRST_DATE);

		mockMvc.perform(get("/api/v1/dashboard/issue-trends")
						.queryParam("issueId", issue.getId().toString())
						.queryParam("from", FIRST_DATE.toString())
						.queryParam("to", SECOND_DATE.toString())
						.header("Authorization", bearer(otherPm)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_FOUND.code()));

		mockMvc.perform(get("/api/v1/dashboard/issue-trends")
						.queryParam("issueId", issue.getId().toString())
						.queryParam("from", "2026-07-12")
						.queryParam("to", "2026-07-11")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()));

		mockMvc.perform(get("/api/v1/dashboard/issue-trends")
						.queryParam("issueId", issue.getId().toString())
						.queryParam("from", "2025-01-01")
						.queryParam("to", "2026-01-02")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()));

		mockMvc.perform(get("/api/v1/dashboard/issue-trends")
						.queryParam("issueId", issue.getId().toString())
						.queryParam("from", "2025-01-01")
						.queryParam("to", "2026-01-01")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/dashboard/issue-trends")
						.queryParam("issueId", issue.getId().toString())
						.header("Authorization", bearer(developer)))
				.andExpect(status().isForbidden());
	}

	@Test
	void ranksTopIssuesByLatestSnapshotGrowthRate() throws Exception {
		Issue fastGrowing = saveIssue(organization, pm, "Fast growing issue");
		Issue steadyGrowing = saveIssue(organization, pm, "Steady growing issue");
		Issue newlyActive = saveIssue(organization, pm, "Newly active issue");
		link(organization, feedback("fast-1", dataset), fastGrowing);
		link(organization, feedback("steady-1", dataset), steadyGrowing);
		link(organization, feedback("steady-2", dataset), steadyGrowing);
		snapshotService.captureOrganization(organization.getId(), FIRST_DATE);

		link(organization, feedback("fast-2", dataset), fastGrowing);
		link(organization, feedback("fast-3", dataset), fastGrowing);
		link(organization, feedback("steady-3", dataset), steadyGrowing);
		link(organization, feedback("newly-active-1", dataset), newlyActive);
		snapshotService.captureOrganization(organization.getId(), SECOND_DATE);

		mockMvc.perform(get("/api/v1/dashboard/top-issues")
						.queryParam("sortBy", "growth_rate")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].issueId").value(fastGrowing.getId()))
				.andExpect(jsonPath("$.data[0].feedbackGrowthRate").value(200.0))
				.andExpect(jsonPath("$.data[1].issueId").value(steadyGrowing.getId()))
				.andExpect(jsonPath("$.data[1].feedbackGrowthRate").value(50.0))
				.andExpect(jsonPath("$.data[2].issueId").value(newlyActive.getId()))
				.andExpect(jsonPath("$.data[2].feedbackGrowthRate").doesNotExist());
	}

	@Test
	void schedulerCapturesEveryOrganization() {
		saveIssue(organization, pm, "Payment failure");
		saveIssue(otherOrganization, otherPm, "Other payment failure");

		snapshotScheduler.captureDailySnapshots();

		LocalDate today = snapshotService.currentDate();
		assertThat(snapshotRepository.findAllByOrganizationAndDate(
				organization.getId(),
				today
		)).hasSize(1);
		assertThat(snapshotRepository.findAllByOrganizationAndDate(
				otherOrganization.getId(),
				today
		)).hasSize(1);
	}

	private Feedback analyzedFeedback(
			Organization feedbackOrganization,
			Dataset feedbackDataset,
			String externalId,
			Sentiment sentiment,
			BigDecimal sentimentScore,
			BigDecimal urgencyScore
	) {
		Feedback feedback = feedback(externalId, feedbackOrganization, feedbackDataset);
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
						sentimentScore,
						"PAYMENT",
						urgencyScore,
						"Feedback summary for " + externalId,
						new BigDecimal("0.9000")
				)
		);
		return feedback;
	}

	private Feedback feedback(String externalId, Dataset feedbackDataset) {
		return feedback(externalId, organization, feedbackDataset);
	}

	private Feedback feedback(
			String externalId,
			Organization feedbackOrganization,
			Dataset feedbackDataset
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
				LocalDateTime.of(2026, 7, 10, 10, 0)
		));
	}

	private Issue saveIssue(Organization issueOrganization, User assignee, String title) {
		return issueRepository.saveAndFlush(new Issue(
				issueOrganization,
				title,
				"Issue description for " + title,
				"PAYMENT",
				Priority.P3,
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

	private User user(Organization userOrganization, String email, Role role) {
		return new User(userOrganization, email, "encoded-password", email, role);
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenProvider.issue(user).value();
	}
}
