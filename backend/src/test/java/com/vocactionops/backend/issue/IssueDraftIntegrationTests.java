package com.vocactionops.backend.issue;

import com.vocactionops.backend.analysis.application.FeedbackAnalysisService;
import com.vocactionops.backend.analysis.application.FeedbackAnalysisService.AnalysisResult;
import com.vocactionops.backend.analysis.domain.Sentiment;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.auth.token.JwtTokenProvider;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.issue.domain.Issue;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IssueDraftIntegrationTests {

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
	private FeedbackAnalysisRepository analysisRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private Organization organization;
	private Organization otherOrganization;
	private User pm;
	private User csUser;
	private User viewer;
	private User otherAdmin;
	private Feedback feedback;
	private long analysisVersion;

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
		feedback = feedbackRepository.save(feedback(
				organization,
				dataset,
				"review-001",
				"Coupon payment failed and the order could not be completed."
		));
		completeAnalysis(feedback, "PAYMENT", "Coupon payment cannot be completed.");
		analysisVersion = analysisRepository.findByFeedbackIdAndFeedbackOrganizationId(
				feedback.getId(),
				organization.getId()
		).orElseThrow().getVersion();
	}

	@Test
	void generatesEditableDraftWhenNoExistingCandidateMatches() throws Exception {
		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/issue-draft", feedback.getId())
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.feedbackId").value(feedback.getId()))
				.andExpect(jsonPath("$.data.analysisVersion").value(analysisVersion))
				.andExpect(jsonPath("$.data.title")
						.value("Coupon payment cannot be completed"))
				.andExpect(jsonPath("$.data.description").value(feedback.getContent()))
				.andExpect(jsonPath("$.data.category").value("PAYMENT"))
				.andExpect(jsonPath("$.data.sentiment").value("NEGATIVE"))
				.andExpect(jsonPath("$.data.urgencyScore").value(0.9))
				.andExpect(jsonPath("$.data.confidenceScore").value(0.88));
	}

	@Test
	void confirmsEditedDraftWithRepresentativeSeedFeedback() throws Exception {
		String responseBody = mockMvc.perform(post(
						"/api/v1/feedbacks/{feedbackId}/issue-draft/confirm",
						feedback.getId()
				)
						.header("Authorization", bearer(pm))
						.contentType(MediaType.APPLICATION_JSON)
						.content(confirmRequest(
								analysisVersion,
								"Coupon checkout failure",
								"Customers cannot complete an order after applying a coupon.",
								pm.getId()
						)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("Coupon checkout failure"))
				.andExpect(jsonPath("$.data.category").value("PAYMENT"))
				.andExpect(jsonPath("$.data.status").value("NEW"))
				.andExpect(jsonPath("$.data.priority").value("P1"))
				.andExpect(jsonPath("$.data.priorityScore").value(68.0))
				.andExpect(jsonPath("$.data.feedbackCount").value(1))
				.andReturn()
				.getResponse()
				.getContentAsString();
		long issueId = objectMapper.readTree(responseBody).path("data").path("id").asLong();

		mockMvc.perform(get("/api/v1/issues/{issueId}/feedbacks", issueId)
						.queryParam("representativeOnly", "true")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].feedbackId").value(feedback.getId()))
				.andExpect(jsonPath("$.data.content[0].representative").value(true))
				.andExpect(jsonPath("$.data.content[0].linkedBy").value("AI"))
				.andExpect(jsonPath("$.data.content[0].similarityScore").value(1.0));

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/issue-draft", feedback.getId())
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code")
						.value(ErrorCode.DUPLICATED_RESOURCE.code()));
	}

	@Test
	void rejectsDraftWhenExistingIssueCandidateMatches() throws Exception {
		issueRepository.save(new Issue(
				organization,
				"Coupon payment failure",
				"Payment failures repeat after applying a coupon.",
				"PAYMENT",
				Priority.P2,
				pm
		));

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/issue-draft", feedback.getId())
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code")
						.value(ErrorCode.ISSUE_CANDIDATE_EXISTS.code()));

		mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/issue-draft/confirm", feedback.getId())
						.header("Authorization", bearer(pm))
						.contentType(MediaType.APPLICATION_JSON)
						.content(confirmRequest(
								analysisVersion,
								"New issue",
								"New issue description",
								null
						)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code")
						.value(ErrorCode.ISSUE_CANDIDATE_EXISTS.code()));
	}

	@Test
	void rejectsStaleDraftAndViewerConfirmation() throws Exception {
		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/issue-draft", feedback.getId())
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.analysisVersion").value(analysisVersion));

		mockMvc.perform(patch("/api/v1/feedbacks/{feedbackId}/analysis", feedback.getId())
						.header("Authorization", bearer(csUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of(
								"fieldName", "category",
								"correctedValue", "CHECKOUT",
								"reason", "결제 승인 이후 주문 완료 단계 문제"
						))))
				.andExpect(status().isOk());

		String request = confirmRequest(
				analysisVersion,
				"Coupon checkout failure",
				"Customers cannot complete checkout.",
				null
		);
		mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/issue-draft/confirm", feedback.getId())
						.header("Authorization", bearer(pm))
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.STALE_RESOURCE.code()));

		mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/issue-draft/confirm", feedback.getId())
						.header("Authorization", bearer(viewer))
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isForbidden());
	}

	@Test
	void rejectsOtherOrganizationFeedbackAndAssignee() throws Exception {
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
		completeAnalysis(otherFeedback, "PAYMENT", "Coupon payment failed.");

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/issue-draft", otherFeedback.getId())
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/issue-draft/confirm", feedback.getId())
						.header("Authorization", bearer(pm))
						.contentType(MediaType.APPLICATION_JSON)
						.content(confirmRequest(
								analysisVersion,
								"Coupon checkout failure",
								"Customers cannot complete checkout.",
								otherAdmin.getId()
						)))
				.andExpect(status().isNotFound());
	}

	@Test
	void serializesConcurrentDraftConfirmationForTheSameFeedback() throws Exception {
		String request = confirmRequest(
				analysisVersion,
				"Coupon checkout failure",
				"Customers cannot complete checkout.",
				null
		);
		String token = bearer(pm);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Integer> first = executor.submit(() -> confirmStatus(
					feedback.getId(),
					request,
					token,
					ready,
					start
			));
			Future<Integer> second = executor.submit(() -> confirmStatus(
					feedback.getId(),
					request,
					token,
					ready,
					start
			));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(List.of(
					first.get(10, TimeUnit.SECONDS),
					second.get(10, TimeUnit.SECONDS)
			))
					.containsExactlyInAnyOrder(200, 409);
		} finally {
			executor.shutdownNow();
		}

		mockMvc.perform(get("/api/v1/issues")
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1));
	}

	private int confirmStatus(
			Long feedbackId,
			String request,
			String token,
			CountDownLatch ready,
			CountDownLatch start
	) throws Exception {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("confirmation start timed out");
		}
		return mockMvc.perform(post("/api/v1/feedbacks/{feedbackId}/issue-draft/confirm", feedbackId)
						.header("Authorization", token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andReturn()
				.getResponse()
				.getStatus();
	}

	private void completeAnalysis(Feedback target, String category, String summary) {
		analysisService.startAnalysis(target.getOrganization().getId(), target.getId(), "classifier-v1");
		analysisService.completeAnalysis(
				target.getOrganization().getId(),
				target.getId(),
				new AnalysisResult(
						Sentiment.NEGATIVE,
						new BigDecimal("-0.85000"),
						category,
						new BigDecimal("0.9000"),
						summary,
						new BigDecimal("0.8800")
				)
		);
	}

	private String confirmRequest(
			long analysisVersion,
			String title,
			String description,
			Long assigneeId
	) throws Exception {
		Map<String, Object> request = new java.util.LinkedHashMap<>();
		request.put("analysisVersion", analysisVersion);
		request.put("title", title);
		request.put("description", description);
		if (assigneeId != null) {
			request.put("assigneeId", assigneeId);
		}
		return json(request);
	}

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
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
				LocalDateTime.of(2026, 7, 25, 10, 0)
		);
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenProvider.issue(user).value();
	}
}
