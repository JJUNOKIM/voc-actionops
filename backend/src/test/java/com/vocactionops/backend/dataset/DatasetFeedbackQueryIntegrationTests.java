package com.vocactionops.backend.dataset;

import com.vocactionops.backend.analysis.repository.AiCorrectionRepository;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.auth.token.JwtTokenProvider;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.dataset.repository.DatasetValidationErrorRepository;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
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
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatasetFeedbackQueryIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DatasetRepository datasetRepository;

	@Autowired
	private FeedbackRepository feedbackRepository;

	@Autowired
	private FeedbackAnalysisRepository analysisRepository;

	@Autowired
	private AiCorrectionRepository correctionRepository;

	@Autowired
	private DatasetValidationErrorRepository validationErrorRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private Organization firstOrganization;
	private Organization secondOrganization;
	private User admin;
	private User developer;
	private User otherAdmin;
	private Dataset appReviewDataset;
	private Dataset csTicketDataset;
	private Dataset otherDataset;
	private Feedback appReviewFeedback;
	private Feedback otherFeedback;

	@BeforeEach
	void setUp() {
		correctionRepository.deleteAll();
		analysisRepository.deleteAll();
		validationErrorRepository.deleteAll();
		feedbackRepository.deleteAll();
		datasetRepository.deleteAll();
		userRepository.deleteAll();
		organizationRepository.deleteAll();

		firstOrganization = organizationRepository.save(new Organization("VOC Team"));
		secondOrganization = organizationRepository.save(new Organization("Other Team"));
		admin = userRepository.save(user(firstOrganization, "admin@example.com", Role.ADMIN));
		developer = userRepository.save(user(firstOrganization, "developer@example.com", Role.DEVELOPER));
		otherAdmin = userRepository.save(user(secondOrganization, "other@example.com", Role.ADMIN));

		appReviewDataset = new Dataset(
				firstOrganization,
				"2026 July App Reviews",
				SourceType.APP_REVIEW,
				"s3://voc-actionops/app-reviews.csv",
				Map.of("review_text", "content"),
				admin
		);
		appReviewDataset.startValidation();
		appReviewDataset.completeValidation(2, 2, 0);
		appReviewDataset = datasetRepository.save(appReviewDataset);
		csTicketDataset = datasetRepository.save(new Dataset(
				firstOrganization,
				"2026 Q2 CS Tickets",
				SourceType.CS_TICKET,
				admin
		));
		otherDataset = datasetRepository.save(new Dataset(
				secondOrganization,
				"Other App Reviews",
				SourceType.APP_REVIEW,
				otherAdmin
		));

		appReviewFeedback = feedbackRepository.save(feedback(
				firstOrganization,
				appReviewDataset,
				"review-001",
				SourceType.APP_REVIEW,
				"Coupon payment failed."
		));
		feedbackRepository.save(feedback(
				firstOrganization,
				appReviewDataset,
				"review-002",
				SourceType.APP_REVIEW,
				"The app closes during payment."
		));
		feedbackRepository.save(feedback(
				firstOrganization,
				csTicketDataset,
				"ticket-001",
				SourceType.CS_TICKET,
				"Delivery is delayed."
		));
		otherFeedback = feedbackRepository.save(feedback(
				secondOrganization,
				otherDataset,
				"other-001",
				SourceType.APP_REVIEW,
				"Other organization feedback."
		));
	}

	@Test
	void filtersAndPaginatesDatasetsWithinAuthenticatedOrganization() throws Exception {
		mockMvc.perform(get("/api/v1/datasets")
						.queryParam("sourceType", "APP_REVIEW")
						.queryParam("status", "VALIDATED")
						.queryParam("page", "0")
						.queryParam("size", "1")
						.header("Authorization", bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content.length()").value(1))
				.andExpect(jsonPath("$.data.content[0].id").value(appReviewDataset.getId()))
				.andExpect(jsonPath("$.data.content[0].status").value("VALIDATED"))
				.andExpect(jsonPath("$.data.page").value(0))
				.andExpect(jsonPath("$.data.size").value(1))
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.totalPages").value(1));
	}

	@Test
	void listsOnlyDatasetsFromAuthenticatedOrganization() throws Exception {
		mockMvc.perform(get("/api/v1/datasets")
						.header("Authorization", bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[*].name", containsInAnyOrder(
						appReviewDataset.getName(),
						csTicketDataset.getName()
				)))
				.andExpect(jsonPath("$.data.content[*].name", not(hasItem(otherDataset.getName()))));
	}

	@Test
	void returnsDatasetDetailAndHidesAnotherOrganizationDataset() throws Exception {
		mockMvc.perform(get("/api/v1/datasets/{datasetId}", appReviewDataset.getId())
						.header("Authorization", bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(appReviewDataset.getId()))
				.andExpect(jsonPath("$.data.columnMapping.review_text").value("content"))
				.andExpect(jsonPath("$.data.createdBy").value(admin.getId()));

		mockMvc.perform(get("/api/v1/datasets/{datasetId}", otherDataset.getId())
						.header("Authorization", bearer(admin)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_FOUND.code()));
	}

	@Test
	void rejectsInvalidDatasetQueryAndDeveloperRole() throws Exception {
		mockMvc.perform(get("/api/v1/datasets")
						.queryParam("sourceType", "UNKNOWN")
						.header("Authorization", bearer(admin)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()));

		mockMvc.perform(get("/api/v1/datasets")
						.queryParam("size", "101")
						.header("Authorization", bearer(admin)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()));

		mockMvc.perform(get("/api/v1/datasets")
						.header("Authorization", bearer(developer)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.code()));
	}

	@Test
	void filtersFeedbacksByDatasetWithoutCrossingOrganizationBoundary() throws Exception {
		mockMvc.perform(get("/api/v1/feedbacks")
						.queryParam("datasetId", appReviewDataset.getId().toString())
						.header("Authorization", bearer(developer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(2))
				.andExpect(jsonPath("$.data.content[*].externalId", containsInAnyOrder(
						"review-001",
						"review-002"
				)))
				.andExpect(jsonPath("$.data.content[*].externalId", not(hasItem("other-001"))));
	}

	@Test
	void returnsFeedbackDetailAndHidesOtherOrganizationFeedback() throws Exception {
		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}", appReviewFeedback.getId())
						.header("Authorization", bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.datasetId").value(appReviewDataset.getId()))
				.andExpect(jsonPath("$.data.externalId").value(appReviewFeedback.getExternalId()))
				.andExpect(jsonPath("$.data.content").value(appReviewFeedback.getContent()));

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}", otherFeedback.getId())
						.header("Authorization", bearer(admin)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_FOUND.code()));
	}

	private User user(Organization organization, String email, Role role) {
		return new User(organization, email, "encoded-password", email, role);
	}

	private Feedback feedback(
			Organization organization,
			Dataset dataset,
			String externalId,
			SourceType sourceType,
			String content
	) {
		return new Feedback(
				organization,
				dataset,
				externalId,
				sourceType,
				"general",
				"mobile-app",
				new BigDecimal("1.0"),
				content,
				"en",
				LocalDateTime.of(2026, 7, 1, 12, 0)
		);
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenProvider.issue(user).value();
	}
}
