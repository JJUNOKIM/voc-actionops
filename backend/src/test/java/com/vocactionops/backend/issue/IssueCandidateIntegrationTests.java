package com.vocactionops.backend.issue;

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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IssueCandidateIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

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

	private Organization organization;
	private Organization otherOrganization;
	private User pm;
	private User csUser;
	private User viewer;
	private User otherAdmin;
	private Feedback analyzedFeedback;
	private Feedback unanalyzedFeedback;

	@BeforeEach
	void setUp() {
		databaseCleaner.clean();

		organization = organizationRepository.save(new Organization("VOC Team"));
		otherOrganization = organizationRepository.save(new Organization("Other Team"));
		pm = userRepository.save(user(organization, "pm@example.com", Role.PM));
		csUser = userRepository.save(user(organization, "cs@example.com", Role.CS));
		viewer = userRepository.save(user(organization, "viewer@example.com", Role.VIEWER));
		otherAdmin = userRepository.save(user(otherOrganization, "other@example.com", Role.ADMIN));

		Dataset dataset = datasetRepository.save(new Dataset(
				organization,
				"App Reviews",
				SourceType.APP_REVIEW,
				pm
		));
		analyzedFeedback = feedbackRepository.save(feedback(
				organization,
				dataset,
				"review-001",
				"Coupon payment failed and the order could not be completed."
		));
		unanalyzedFeedback = feedbackRepository.save(feedback(
				organization,
				dataset,
				"review-002",
				"Payment takes too long."
		));

		analysisService.startAnalysis(organization.getId(), analyzedFeedback.getId(), "classifier-v1");
		analysisService.completeAnalysis(
				organization.getId(),
				analyzedFeedback.getId(),
				new AnalysisResult(
						Sentiment.NEGATIVE,
						new BigDecimal("-0.85000"),
						"PAYMENT",
						new BigDecimal("0.9000"),
						"Coupon payment cannot be completed.",
						new BigDecimal("0.8800")
				)
		);
	}

	@Test
	void recommendsSimilarOpenIssuesInScoreOrderWithinOrganization() throws Exception {
		Issue similar = saveIssue(
				organization,
				"Coupon payment failure",
				"Payment failures repeat after applying a coupon.",
				"PAYMENT"
		);
		Issue lessSimilar = saveIssue(
				organization,
				"Card payment declined",
				"A bank card is rejected during checkout.",
				"PAYMENT"
		);
		Issue wrongCategory = saveIssue(
				organization,
				"Coupon payment failure",
				"Payment failures repeat after applying a coupon.",
				"DELIVERY"
		);
		Issue closed = saveIssue(
				organization,
				"Coupon order cannot be completed",
				"Coupon payment fails every time.",
				"PAYMENT"
		);
		close(closed);
		Issue otherOrganizationIssue = saveIssue(
				otherOrganization,
				"Coupon payment failure",
				"Payment failures repeat after applying a coupon.",
				"PAYMENT"
		);

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/issue-candidates", analyzedFeedback.getId())
						.queryParam("limit", "2")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].issueId").value(similar.getId()))
				.andExpect(jsonPath("$.data[0].matchSignals.categoryMatched").value(true))
				.andExpect(jsonPath("$.data[0].matchSignals.categoryScore").value(0.35))
				.andExpect(jsonPath("$.data[0].similarityScore").isNumber())
				.andExpect(jsonPath("$.data[*].issueId", hasItem(lessSimilar.getId().intValue())))
				.andExpect(jsonPath("$.data[*].issueId", not(hasItem(wrongCategory.getId().intValue()))))
				.andExpect(jsonPath("$.data[*].issueId", not(hasItem(closed.getId().intValue()))))
				.andExpect(jsonPath(
						"$.data[*].issueId",
						not(hasItem(otherOrganizationIssue.getId().intValue()))
				));
	}

	@Test
	void confirmsCurrentCandidateAndRecalculatesIssuePriority() throws Exception {
		Issue issue = saveIssue(
				organization,
				"Coupon payment failure",
				"Payment failures repeat after applying a coupon.",
				"PAYMENT"
		);

		String candidateBody = mockMvc.perform(get(
						"/api/v1/feedbacks/{feedbackId}/issue-candidates",
						analyzedFeedback.getId()
				)
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		BigDecimal candidateScore = objectMapper.readTree(candidateBody)
				.path("data")
				.get(0)
				.path("similarityScore")
				.decimalValue();

		mockMvc.perform(post(
						"/api/v1/feedbacks/{feedbackId}/issue-candidates/{issueId}/confirm",
						analyzedFeedback.getId(),
						issue.getId()
				)
						.header("Authorization", bearer(csUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("representative", true))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedbackId").value(analyzedFeedback.getId()))
				.andExpect(jsonPath("$.data.linkedBy").value("AI"))
				.andExpect(jsonPath("$.data.similarityScore").value(candidateScore.doubleValue()))
				.andExpect(jsonPath("$.data.representative").value(true));

		mockMvc.perform(get("/api/v1/issues/{issueId}", issue.getId())
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedbackCount").value(1))
				.andExpect(jsonPath("$.data.priority").value("P1"))
				.andExpect(jsonPath("$.data.priorityScore").value(68.0));

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/issue-candidates", analyzedFeedback.getId())
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	void requiresSuccessfulAnalysisAndCandidateConfirmationPermission() throws Exception {
		Issue issue = saveIssue(
				organization,
				"Coupon payment failure",
				"Payment failures repeat after applying a coupon.",
				"PAYMENT"
		);

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/issue-candidates", unanalyzedFeedback.getId())
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.ANALYSIS_NOT_READY.code()));

		mockMvc.perform(post(
						"/api/v1/feedbacks/{feedbackId}/issue-candidates/{issueId}/confirm",
						analyzedFeedback.getId(),
						issue.getId()
				)
						.header("Authorization", bearer(viewer))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("representative", false))))
				.andExpect(status().isForbidden());
	}

	@Test
	void rejectsLowSimilarityAndOtherOrganizationResources() throws Exception {
		Issue unrelated = saveIssue(
				organization,
				"App closes on checkout",
				"The mobile application crashes without an error message.",
				"PAYMENT"
		);

		mockMvc.perform(post(
						"/api/v1/feedbacks/{feedbackId}/issue-candidates/{issueId}/confirm",
						analyzedFeedback.getId(),
						unrelated.getId()
				)
						.header("Authorization", bearer(csUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("representative", false))))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/issue-candidates", analyzedFeedback.getId())
						.queryParam("limit", "11")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()));

		Dataset otherDataset = datasetRepository.save(new Dataset(
				otherOrganization,
				"Other Reviews",
				SourceType.APP_REVIEW,
				otherAdmin
		));
		Feedback otherFeedback = feedbackRepository.save(feedback(
				otherOrganization,
				otherDataset,
				"other-001",
				"Coupon payment failed."
		));

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/issue-candidates", otherFeedback.getId())
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsCandidateThatNoLongerMatchesCorrectedAnalysis() throws Exception {
		Issue issue = saveIssue(
				organization,
				"Coupon payment failure",
				"Payment failures repeat after applying a coupon.",
				"PAYMENT"
		);

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/issue-candidates", analyzedFeedback.getId())
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].issueId").value(issue.getId()));

		mockMvc.perform(patch(
						"/api/v1/feedbacks/{feedbackId}/analysis",
						analyzedFeedback.getId()
				)
						.header("Authorization", bearer(csUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of(
								"fieldName", "category",
								"correctedValue", "CHECKOUT",
								"reason", "결제 완료 이후 화면 이동 문제로 재분류"
						))))
				.andExpect(status().isOk());

		mockMvc.perform(post(
						"/api/v1/feedbacks/{feedbackId}/issue-candidates/{issueId}/confirm",
						analyzedFeedback.getId(),
						issue.getId()
				)
						.header("Authorization", bearer(csUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("representative", false))))
				.andExpect(status().isNotFound());
	}

	private Issue saveIssue(
			Organization issueOrganization,
			String title,
			String description,
			String category
	) {
		User assignee = issueOrganization == organization ? pm : otherAdmin;
		return issueRepository.save(new Issue(
				issueOrganization,
				title,
				description,
				category,
				Priority.P2,
				assignee
		));
	}

	private void close(Issue issue) {
		issue.changeStatus(IssueStatus.TRIAGED);
		issue.changeStatus(IssueStatus.ASSIGNED);
		issue.changeStatus(IssueStatus.IN_PROGRESS);
		issue.changeStatus(IssueStatus.RESOLVED);
		issue.changeStatus(IssueStatus.MONITORING);
		issue.changeStatus(IssueStatus.CLOSED);
		issueRepository.save(issue);
	}

	private User user(Organization userOrganization, String email, Role role) {
		return new User(userOrganization, email, "encoded-password", email, role);
	}

	private Feedback feedback(
			Organization feedbackOrganization,
			Dataset dataset,
			String externalId,
			String content
	) {
		return new Feedback(
				feedbackOrganization,
				dataset,
				externalId,
				SourceType.APP_REVIEW,
				"new-customer",
				"mobile-app",
				new BigDecimal("1.0"),
				content,
				"en",
				LocalDateTime.of(2026, 7, 20, 10, 0)
		);
	}

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenProvider.issue(user).value();
	}
}
