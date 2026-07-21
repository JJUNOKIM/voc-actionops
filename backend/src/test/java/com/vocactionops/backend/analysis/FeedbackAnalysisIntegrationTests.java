package com.vocactionops.backend.analysis;

import com.vocactionops.backend.analysis.application.FeedbackAnalysisService;
import com.vocactionops.backend.analysis.application.FeedbackAnalysisService.AnalysisResult;
import com.vocactionops.backend.analysis.application.FeedbackAnalysisView;
import com.vocactionops.backend.analysis.domain.AiCorrectionField;
import com.vocactionops.backend.analysis.domain.AnalysisStatus;
import com.vocactionops.backend.analysis.domain.Sentiment;
import com.vocactionops.backend.analysis.repository.AiCorrectionRepository;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.auth.token.JwtTokenProvider;
import com.vocactionops.backend.common.exception.CustomException;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedbackAnalysisIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private FeedbackAnalysisService analysisService;

	@Autowired
	private FeedbackAnalysisRepository analysisRepository;

	@Autowired
	private AiCorrectionRepository correctionRepository;

	@Autowired
	private FeedbackRepository feedbackRepository;

	@Autowired
	private DatasetRepository datasetRepository;

	@Autowired
	private DatasetValidationErrorRepository validationErrorRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	private Organization organization;
	private Organization otherOrganization;
	private User admin;
	private User csUser;
	private User viewer;
	private User otherAdmin;
	private Feedback feedback;

	@BeforeEach
	void setUp() {
		correctionRepository.deleteAll();
		analysisRepository.deleteAll();
		validationErrorRepository.deleteAll();
		feedbackRepository.deleteAll();
		datasetRepository.deleteAll();
		userRepository.deleteAll();
		organizationRepository.deleteAll();

		organization = organizationRepository.save(new Organization("VOC Team"));
		otherOrganization = organizationRepository.save(new Organization("Other Team"));
		admin = userRepository.save(user(organization, "admin@example.com", Role.ADMIN));
		csUser = userRepository.save(user(organization, "cs@example.com", Role.CS));
		viewer = userRepository.save(user(organization, "viewer@example.com", Role.VIEWER));
		otherAdmin = userRepository.save(user(otherOrganization, "other@example.com", Role.ADMIN));

		Dataset dataset = datasetRepository.save(new Dataset(
				organization,
				"App Reviews",
				SourceType.APP_REVIEW,
				admin
		));
		feedback = feedbackRepository.save(new Feedback(
				organization,
				dataset,
				"review-001",
				SourceType.APP_REVIEW,
				"new-customer",
				"mobile-app",
				new BigDecimal("1.0"),
				"Coupon payment failed.",
				"en",
				LocalDateTime.of(2026, 7, 1, 12, 0)
		));
	}

	@Test
	void storesSuccessfulAnalysisAndReturnsItWithFeedbackDetail() throws Exception {
		FeedbackAnalysisView pending = analysisService.startAnalysis(
				organization.getId(),
				feedback.getId(),
				"feedback-classifier-v1"
		);
		assertThat(pending.status()).isEqualTo(AnalysisStatus.PENDING);

		FeedbackAnalysisView completed = analysisService.completeAnalysis(
				organization.getId(),
				feedback.getId(),
				validResult()
		);
		assertThat(completed.status()).isEqualTo(AnalysisStatus.SUCCESS);

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}", feedback.getId())
						.header("Authorization", bearer(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(feedback.getId()))
				.andExpect(jsonPath("$.data.analysis.status").value("SUCCESS"))
				.andExpect(jsonPath("$.data.analysis.sentiment").value("NEGATIVE"))
				.andExpect(jsonPath("$.data.analysis.category").value("PAYMENT"))
				.andExpect(jsonPath("$.data.analysis.errorMessage").isEmpty());
	}

	@Test
	void storesFailureReasonAndAllowsOnlyFailedAnalysisToRestart() {
		analysisService.startAnalysis(
				organization.getId(),
				feedback.getId(),
				"feedback-classifier-v1"
		);
		FeedbackAnalysisView failed = analysisService.failAnalysis(
				organization.getId(),
				feedback.getId(),
				"provider timeout"
		);

		assertThat(failed.status()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(failed.errorMessage()).isEqualTo("provider timeout");
		assertThat(failed.analyzedAt()).isNotNull();

		FeedbackAnalysisView restarted = analysisService.startAnalysis(
				organization.getId(),
				feedback.getId(),
				"feedback-classifier-v2"
		);
		assertThat(restarted.status()).isEqualTo(AnalysisStatus.PENDING);
		assertThat(restarted.modelName()).isEqualTo("feedback-classifier-v2");
		assertThat(restarted.errorMessage()).isNull();

		assertThatThrownBy(() -> analysisService.startAnalysis(
				organization.getId(),
				feedback.getId(),
				"feedback-classifier-v2"
		))
				.isInstanceOf(CustomException.class)
				.extracting(exception -> ((CustomException) exception).errorCode())
				.isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
	}

	@Test
	void correctsSuccessfulAnalysisAndStoresAuditHistory() throws Exception {
		completeAnalysis();

		mockMvc.perform(patch("/api/v1/feedbacks/{feedbackId}/analysis", feedback.getId())
						.header("Authorization", bearer(csUser))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(Map.of(
								"fieldName", "category",
								"correctedValue", "CHECKOUT",
								"reason", "The failure occurs before payment authorization."
						))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.category").value("CHECKOUT"))
				.andExpect(jsonPath("$.message").value("AI 분석 결과가 수정되었습니다."));

		assertThat(correctionRepository.findAll()).singleElement().satisfies(correction -> {
			assertThat(correction.getFieldName()).isEqualTo(AiCorrectionField.CATEGORY);
			assertThat(correction.getPreviousValue()).isEqualTo("PAYMENT");
			assertThat(correction.getCorrectedValue()).isEqualTo("CHECKOUT");
			assertThat(correction.getCorrectedBy().getId()).isEqualTo(csUser.getId());
		});

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/corrections", feedback.getId())
						.header("Authorization", bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.content[0].fieldName").value("category"))
				.andExpect(jsonPath("$.data.content[0].aiValue").value("PAYMENT"))
				.andExpect(jsonPath("$.data.content[0].correctedValue").value("CHECKOUT"));

		mockMvc.perform(patch("/api/v1/feedbacks/{feedbackId}/analysis", feedback.getId())
						.header("Authorization", bearer(viewer))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(Map.of(
								"fieldName", "category",
								"correctedValue", "PAYMENT",
								"reason", "Viewer must not edit."
						))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.code()));
	}

	@Test
	void rejectsCorrectionUntilAnalysisSucceeds() throws Exception {
		analysisService.startAnalysis(
				organization.getId(),
				feedback.getId(),
				"feedback-classifier-v1"
		);

		mockMvc.perform(patch("/api/v1/feedbacks/{feedbackId}/analysis", feedback.getId())
						.header("Authorization", bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsBytes(Map.of(
								"fieldName", "sentiment",
								"correctedValue", "NEUTRAL",
								"reason", "Manual review"
						))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code")
						.value(ErrorCode.INVALID_STATUS_TRANSITION.code()));

		assertThat(correctionRepository.count()).isZero();
	}

	@Test
	void hidesAnalysisAndCorrectionHistoryAcrossOrganizations() throws Exception {
		completeAnalysis();

		assertThatThrownBy(() -> analysisService.getAnalysis(
				otherOrganization.getId(),
				feedback.getId()
		))
				.isInstanceOf(CustomException.class)
				.extracting(exception -> ((CustomException) exception).errorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);

		mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}/corrections", feedback.getId())
						.header("Authorization", bearer(otherAdmin)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_FOUND.code()));
	}

	private void completeAnalysis() {
		analysisService.startAnalysis(
				organization.getId(),
				feedback.getId(),
				"feedback-classifier-v1"
		);
		analysisService.completeAnalysis(
				organization.getId(),
				feedback.getId(),
				validResult()
		);
	}

	private AnalysisResult validResult() {
		return new AnalysisResult(
				Sentiment.NEGATIVE,
				new BigDecimal("-0.85000"),
				"PAYMENT",
				new BigDecimal("0.9000"),
				"The customer cannot complete payment with a coupon.",
				new BigDecimal("0.8800")
		);
	}

	private User user(Organization organization, String email, Role role) {
		return new User(organization, email, "encoded-password", email, role);
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenProvider.issue(user).value();
	}
}
