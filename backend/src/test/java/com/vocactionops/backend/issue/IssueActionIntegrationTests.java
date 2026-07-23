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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
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
class IssueActionIntegrationTests {

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
	private FeedbackAnalysisService analysisService;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private Organization organization;
	private User admin;
	private User pm;
	private User csUser;
	private User developer;
	private User otherDeveloper;
	private User viewer;
	private User otherAdmin;
	private User otherOrganizationDeveloper;
	private Feedback feedback;
	private Feedback secondFeedback;
	private Feedback otherFeedback;

	@BeforeEach
	void setUp() {
		databaseCleaner.clean();

		organization = organizationRepository.save(new Organization("VOC Team"));
		Organization otherOrganization = organizationRepository.save(new Organization("Other Team"));
		admin = userRepository.save(user(organization, "admin@example.com", Role.ADMIN));
		pm = userRepository.save(user(organization, "pm@example.com", Role.PM));
		csUser = userRepository.save(user(organization, "cs@example.com", Role.CS));
		developer = userRepository.save(user(organization, "developer@example.com", Role.DEVELOPER));
		otherDeveloper = userRepository.save(user(
				organization,
				"other-developer@example.com",
				Role.DEVELOPER
		));
		viewer = userRepository.save(user(organization, "viewer@example.com", Role.VIEWER));
		otherAdmin = userRepository.save(user(otherOrganization, "other-admin@example.com", Role.ADMIN));
		otherOrganizationDeveloper = userRepository.save(user(
				otherOrganization,
				"other-developer@other.example.com",
				Role.DEVELOPER
		));

		Dataset dataset = datasetRepository.save(new Dataset(
				organization,
				"App Reviews",
				SourceType.APP_REVIEW,
				admin
		));
		Dataset otherDataset = datasetRepository.save(new Dataset(
				otherOrganization,
				"Other App Reviews",
				SourceType.APP_REVIEW,
				otherAdmin
		));
		feedback = feedbackRepository.save(feedback(
				organization,
				dataset,
				"review-001",
				"Coupon payment failed.",
				LocalDateTime.of(2026, 7, 1, 12, 0)
		));
		secondFeedback = feedbackRepository.save(feedback(
				organization,
				dataset,
				"review-002",
				"The app closes during payment.",
				LocalDateTime.of(2026, 7, 3, 9, 30)
		));
		otherFeedback = feedbackRepository.save(feedback(
				otherOrganization,
				otherDataset,
				"other-001",
				"Other organization feedback.",
				LocalDateTime.of(2026, 7, 2, 10, 0)
		));

		analysisService.startAnalysis(organization.getId(), feedback.getId(), "classifier-v1");
		analysisService.completeAnalysis(
				organization.getId(),
				feedback.getId(),
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
	void createsLinksAndQueriesIssueWithFeedbackAggregates() throws Exception {
		long issueId = createIssue(pm, developer.getId());

		linkFeedback(csUser, feedback.getId(), issueId, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedbackId").value(feedback.getId()))
				.andExpect(jsonPath("$.data.representative").value(true))
				.andExpect(jsonPath("$.data.linkedBy").value("MANUAL"));
		linkFeedback(csUser, secondFeedback.getId(), issueId, false)
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/issues")
						.queryParam("priority", "P1")
						.queryParam("category", "PAYMENT")
						.queryParam("keyword", "coupon")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].id").value(issueId))
				.andExpect(jsonPath("$.data.content[0].feedbackCount").value(2))
				.andExpect(jsonPath("$.data.content[0].negativeCount").value(1))
				.andExpect(jsonPath("$.data.content[0].priorityScore").value(69.5))
				.andExpect(jsonPath("$.data.content[0].assigneeId").value(developer.getId()));

		mockMvc.perform(get("/api/v1/issues/{issueId}", issueId)
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedbackCount").value(2))
				.andExpect(jsonPath("$.data.negativeCount").value(1))
				.andExpect(jsonPath("$.data.priority").value("P1"))
				.andExpect(jsonPath("$.data.priorityScore").value(69.5))
				.andExpect(jsonPath("$.data.firstSeenAt").value("2026-07-01T12:00:00"))
				.andExpect(jsonPath("$.data.lastSeenAt").value("2026-07-03T09:30:00"))
				.andExpect(jsonPath("$.data.actions").isEmpty());

		mockMvc.perform(get("/api/v1/issues/{issueId}/feedbacks", issueId)
						.queryParam("representativeOnly", "true")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[*].externalId", contains("review-001")));
	}

	@Test
	void recalculatesPriorityAfterAnalysisCorrection() throws Exception {
		long issueId = createIssue(pm, developer.getId());
		linkFeedback(csUser, feedback.getId(), issueId, true)
				.andExpect(status().isOk());

		correctAnalysis(feedback.getId(), "sentiment", "POSITIVE", "원문은 결제 성공 경험을 설명함")
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/issues/{issueId}", issueId)
					.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.priority").value("P3"))
				.andExpect(jsonPath("$.data.priorityScore").value(33.0));

		correctAnalysis(feedback.getId(), "urgency_score", "0.2", "즉시 대응이 필요한 장애는 아님")
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/issues/{issueId}", issueId)
					.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.priority").value("P3"))
				.andExpect(jsonPath("$.data.priorityScore").value(8.5));
	}

	@Test
	void keepsManualPriorityUntilLinkedFeedbackAnalysisCompletes() throws Exception {
		long issueId = createIssue(pm, developer.getId());

		linkFeedback(csUser, secondFeedback.getId(), issueId, false)
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/issues/{issueId}", issueId)
					.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.priority").value("P1"))
				.andExpect(jsonPath("$.data.priorityScore").isEmpty());

		analysisService.startAnalysis(organization.getId(), secondFeedback.getId(), "classifier-v1");
		analysisService.completeAnalysis(
				organization.getId(),
				secondFeedback.getId(),
				new AnalysisResult(
						Sentiment.POSITIVE,
						new BigDecimal("0.80000"),
						"PAYMENT",
						new BigDecimal("0.0000"),
						"결제가 정상적으로 완료됨",
						new BigDecimal("0.9000")
				)
		);

		mockMvc.perform(get("/api/v1/issues/{issueId}", issueId)
					.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.priority").value("P3"))
				.andExpect(jsonPath("$.data.priorityScore").value(1.5));
	}

	@Test
	void enforcesIssueStateTransitionsAndAssigneePermission() throws Exception {
		long issueId = createIssue(pm, developer.getId());

		changeIssueStatus(otherDeveloper, issueId, "TRIAGED")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.code()));
		changeIssueStatus(developer, issueId, "IN_PROGRESS")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_STATUS_TRANSITION.code()));

		changeIssueStatus(developer, issueId, "TRIAGED")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("TRIAGED"));
		changeIssueStatus(developer, issueId, "ASSIGNED")
				.andExpect(status().isOk());
		changeIssueStatus(developer, issueId, "IN_PROGRESS")
				.andExpect(status().isOk());
		changeIssueStatus(developer, issueId, "RESOLVED")
				.andExpect(status().isOk());
		changeIssueStatus(pm, issueId, "MONITORING")
				.andExpect(status().isOk());
		changeIssueStatus(pm, issueId, "IN_PROGRESS")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

		changeIssueStatus(viewer, issueId, "RESOLVED")
				.andExpect(status().isForbidden());
	}

	@Test
	void requiresAssigneeBeforeIssueCanBecomeAssigned() throws Exception {
		long issueId = createIssue(pm, null);

		changeIssueStatus(pm, issueId, "TRIAGED").andExpect(status().isOk());
		changeIssueStatus(pm, issueId, "ASSIGNED")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_STATUS_TRANSITION.code()));

		mockMvc.perform(patch("/api/v1/issues/{issueId}/assignee", issueId)
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("assigneeId", developer.getId()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.assigneeId").value(developer.getId()));

		changeIssueStatus(developer, issueId, "ASSIGNED")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ASSIGNED"));
	}

	@Test
	void createsActionAndAllowsOnlyItsAssigneeToCompleteIt() throws Exception {
		long issueId = createIssue(pm, developer.getId());
		String responseBody = mockMvc.perform(post("/api/v1/issues/{issueId}/actions", issueId)
						.header("Authorization", bearer(pm))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of(
								"title", "Inspect coupon payment logs",
								"description", "Check the payment authorization boundary.",
								"assigneeId", developer.getId(),
								"dueDate", "2026-08-01"
						))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("TODO"))
				.andExpect(jsonPath("$.data.assigneeId").value(developer.getId()))
				.andReturn()
				.getResponse()
				.getContentAsString();
		long actionId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

		changeActionStatus(otherDeveloper, actionId, "IN_PROGRESS")
				.andExpect(status().isForbidden());
		changeActionStatus(developer, actionId, "IN_PROGRESS")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.completedAt").isEmpty());
		changeActionStatus(developer, actionId, "DONE")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("DONE"))
				.andExpect(jsonPath("$.data.completedAt").isNotEmpty());
		changeActionStatus(developer, actionId, "IN_PROGRESS")
				.andExpect(status().isConflict());

		mockMvc.perform(get("/api/v1/issues/{issueId}", issueId)
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.actions[0].id").value(actionId))
				.andExpect(jsonPath("$.data.actions[0].status").value("DONE"));
	}

	@Test
	void rejectsDuplicateLinksAndViewerChanges() throws Exception {
		long issueId = createIssue(pm, developer.getId());
		linkFeedback(csUser, feedback.getId(), issueId, true).andExpect(status().isOk());

		linkFeedback(csUser, feedback.getId(), issueId, false)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.DUPLICATED_RESOURCE.code()));
		linkFeedback(viewer, secondFeedback.getId(), issueId, false)
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/issues")
						.header("Authorization", bearer(viewer))
						.contentType(MediaType.APPLICATION_JSON)
						.content(createIssueJson(developer.getId())))
				.andExpect(status().isForbidden());
	}

	@Test
	void hidesOtherOrganizationIssuesActionsAndFeedbackLinks() throws Exception {
		long otherIssueId = createIssue(otherAdmin, otherOrganizationDeveloper.getId());

		mockMvc.perform(get("/api/v1/issues/{issueId}", otherIssueId)
						.header("Authorization", bearer(admin)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_FOUND.code()));

		mockMvc.perform(post("/api/v1/issues")
						.header("Authorization", bearer(pm))
						.contentType(MediaType.APPLICATION_JSON)
						.content(createIssueJson(otherOrganizationDeveloper.getId())))
				.andExpect(status().isNotFound());

		linkFeedback(csUser, feedback.getId(), otherIssueId, false)
				.andExpect(status().isNotFound());
		linkFeedback(otherAdmin, otherFeedback.getId(), otherIssueId, true)
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/issues")
						.header("Authorization", bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content[*].id", not(hasItem((int) otherIssueId))));
	}

	private long createIssue(User user, Long assigneeId) throws Exception {
		String responseBody = mockMvc.perform(post("/api/v1/issues")
						.header("Authorization", bearer(user))
						.contentType(MediaType.APPLICATION_JSON)
						.content(createIssueJson(assigneeId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("NEW"))
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode response = objectMapper.readTree(responseBody);
		return response.path("data").path("id").asLong();
	}

	private String createIssueJson(Long assigneeId) throws Exception {
		Map<String, Object> request = new java.util.LinkedHashMap<>();
		request.put("title", "Coupon payment failure");
		request.put("description", "Payment failures repeat after applying a coupon.");
		request.put("category", "PAYMENT");
		request.put("priority", "P1");
		if (assigneeId != null) {
			request.put("assigneeId", assigneeId);
		}
		return json(request);
	}

	private org.springframework.test.web.servlet.ResultActions linkFeedback(
			User user,
			Long feedbackId,
			long issueId,
			boolean representative
	) throws Exception {
		return mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/issue-links", feedbackId)
				.header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
						"issueId", issueId,
						"representative", representative
				))));
	}

	private org.springframework.test.web.servlet.ResultActions changeIssueStatus(
			User user,
			long issueId,
			String status
	) throws Exception {
		return mockMvc.perform(patch("/api/v1/issues/{issueId}/status", issueId)
				.header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("status", status))));
	}

	private org.springframework.test.web.servlet.ResultActions correctAnalysis(
			Long feedbackId,
			String fieldName,
			String correctedValue,
			String reason
	) throws Exception {
		return mockMvc.perform(patch("/api/v1/feedbacks/{feedbackId}/analysis", feedbackId)
				.header("Authorization", bearer(csUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
						"fieldName", fieldName,
						"correctedValue", correctedValue,
						"reason", reason
				))));
	}

	private org.springframework.test.web.servlet.ResultActions changeActionStatus(
			User user,
			long actionId,
			String status
	) throws Exception {
		return mockMvc.perform(patch("/api/v1/actions/{actionId}/status", actionId)
				.header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("status", status))));
	}

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}

	private User user(Organization organization, String email, Role role) {
		return new User(organization, email, "encoded-password", email, role);
	}

	private Feedback feedback(
			Organization organization,
			Dataset dataset,
			String externalId,
			String content,
			LocalDateTime createdAt
	) {
		return new Feedback(
				organization,
				dataset,
				externalId,
				SourceType.APP_REVIEW,
				"new-customer",
				"mobile-app",
				new BigDecimal("1.0"),
				content,
				"en",
				createdAt
		);
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenProvider.issue(user).value();
	}
}
