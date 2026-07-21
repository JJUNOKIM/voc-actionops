package com.vocactionops.backend.analysis;

import com.vocactionops.backend.analysis.application.FeedbackAnalysisService;
import com.vocactionops.backend.analysis.client.AiWorkerClient;
import com.vocactionops.backend.analysis.client.AiWorkerClient.AnalysisRequest;
import com.vocactionops.backend.analysis.client.AiWorkerClient.AnalysisResult;
import com.vocactionops.backend.analysis.client.AiWorkerException;
import com.vocactionops.backend.analysis.domain.AnalysisStatus;
import com.vocactionops.backend.analysis.domain.Sentiment;
import com.vocactionops.backend.analysis.job.application.AnalysisJobRecovery;
import com.vocactionops.backend.analysis.job.domain.AnalysisJob;
import com.vocactionops.backend.analysis.job.domain.AnalysisJobItem;
import com.vocactionops.backend.analysis.job.domain.AnalysisJobItemStatus;
import com.vocactionops.backend.analysis.job.domain.AnalysisJobStatus;
import com.vocactionops.backend.analysis.job.repository.AnalysisJobItemRepository;
import com.vocactionops.backend.analysis.job.repository.AnalysisJobRepository;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.auth.token.JwtTokenProvider;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.domain.DatasetStatus;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatasetAnalysisJobIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DatasetRepository datasetRepository;

	@Autowired
	private FeedbackRepository feedbackRepository;

	@Autowired
	private AnalysisJobRepository jobRepository;

	@Autowired
	private AnalysisJobItemRepository itemRepository;

	@Autowired
	private FeedbackAnalysisRepository analysisRepository;

	@Autowired
	private FeedbackAnalysisService analysisService;

	@Autowired
	private AnalysisJobRecovery jobRecovery;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private DatabaseCleaner databaseCleaner;

	@MockitoBean
	private AiWorkerClient aiWorkerClient;

	private Organization organization;
	private Organization otherOrganization;
	private User admin;
	private User pm;
	private User csUser;
	private User otherAdmin;

	@BeforeEach
	void setUp() {
		databaseCleaner.clean();
		organization = organizationRepository.save(new Organization("VOC Team"));
		otherOrganization = organizationRepository.save(new Organization("Other Team"));
		admin = userRepository.save(user(organization, "admin@example.com", Role.ADMIN));
		pm = userRepository.save(user(organization, "pm@example.com", Role.PM));
		csUser = userRepository.save(user(organization, "cs@example.com", Role.CS));
		otherAdmin = userRepository.save(user(
				otherOrganization,
				"other@example.com",
				Role.ADMIN
		));
	}

	@Test
	void startsJobWithoutWaitingAndReportsCompletedProgress() throws Exception {
		Dataset dataset = validatedDataset(organization, pm, 2);
		CountDownLatch workerStarted = new CountDownLatch(1);
		CountDownLatch releaseWorker = new CountDownLatch(1);
		when(aiWorkerClient.analyze(any())).thenAnswer(invocation -> {
			workerStarted.countDown();
			releaseWorker.await(5, TimeUnit.SECONDS);
			return successfulResult(invocation.getArgument(0));
		});

		String responseBody = mockMvc.perform(post(
						"/api/v1/datasets/{datasetId}/analyze",
						dataset.getId()
				)
					.header("Authorization", bearer(pm)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.datasetId").value(dataset.getId()))
				.andExpect(jsonPath("$.data.status").value("ANALYZING"))
				.andExpect(jsonPath("$.data.jobStatus").value("PENDING"))
				.andExpect(jsonPath("$.message").value("AI 분석 작업이 시작되었습니다."))
				.andReturn()
				.getResponse()
				.getContentAsString();
		String jobId = objectMapper.readTree(responseBody).path("data").path("jobId").stringValue();

		assertThat(workerStarted.await(3, TimeUnit.SECONDS)).isTrue();
		releaseWorker.countDown();
		AnalysisJob completed = awaitTerminalJob(jobId);

		assertThat(completed.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
		assertThat(completed.getProcessedCount()).isEqualTo(2);
		assertThat(completed.getSuccessCount()).isEqualTo(2);
		assertThat(completed.getFailedCount()).isZero();
		assertThat(datasetRepository.findById(dataset.getId()).orElseThrow().getStatus())
				.isEqualTo(DatasetStatus.ANALYZED);
		assertThat(analysisRepository.findAll())
				.allSatisfy(analysis -> {
					assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.SUCCESS);
					assertThat(analysis.getModelName()).isEqualTo("deterministic-v1");
				});
		verify(aiWorkerClient, times(2)).analyze(any());

		mockMvc.perform(get(
						"/api/v1/datasets/{datasetId}/analysis-status",
						dataset.getId()
				)
					.header("Authorization", bearer(csUser)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ANALYZED"))
				.andExpect(jsonPath("$.data.jobStatus").value("COMPLETED"))
				.andExpect(jsonPath("$.data.processedCount").value(2))
				.andExpect(jsonPath("$.data.successCount").value(2))
				.andExpect(jsonPath("$.data.failedCount").value(0))
				.andExpect(jsonPath("$.data.progressRate").value(100.0));
	}

	@Test
	void rejectsDuplicateInvalidAndUnauthorizedStartsAndIsolatesOrganizations() throws Exception {
		Dataset dataset = validatedDataset(organization, admin, 1);
		Dataset uploaded = datasetRepository.save(new Dataset(
				organization,
				"Uploaded only",
				SourceType.SURVEY,
				admin
		));
		CountDownLatch workerStarted = new CountDownLatch(1);
		CountDownLatch releaseWorker = new CountDownLatch(1);
		when(aiWorkerClient.analyze(any())).thenAnswer(invocation -> {
			workerStarted.countDown();
			releaseWorker.await(5, TimeUnit.SECONDS);
			return successfulResult(invocation.getArgument(0));
		});

		try {
			mockMvc.perform(post("/api/v1/datasets/{datasetId}/analyze", dataset.getId())
						.header("Authorization", bearer(admin)))
					.andExpect(status().isOk());
			assertThat(workerStarted.await(3, TimeUnit.SECONDS)).isTrue();

			mockMvc.perform(post("/api/v1/datasets/{datasetId}/analyze", dataset.getId())
						.header("Authorization", bearer(admin)))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.error.code")
							.value(ErrorCode.INVALID_STATUS_TRANSITION.code()));

			mockMvc.perform(post("/api/v1/datasets/{datasetId}/analyze", uploaded.getId())
						.header("Authorization", bearer(admin)))
					.andExpect(status().isConflict());

			Dataset csDataset = validatedDataset(organization, admin, 1);
			mockMvc.perform(post("/api/v1/datasets/{datasetId}/analyze", csDataset.getId())
						.header("Authorization", bearer(csUser)))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.code()));

			mockMvc.perform(get(
							"/api/v1/datasets/{datasetId}/analysis-status",
							dataset.getId()
					)
						.header("Authorization", bearer(otherAdmin)))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_FOUND.code()));
		} finally {
			releaseWorker.countDown();
		}
		awaitLatestJob(dataset.getId());
	}

	@Test
	void retriesFailedFeedbackAndContinuesRemainingItems() throws Exception {
		Dataset dataset = validatedDataset(organization, admin, 2);
		List<Feedback> feedbacks = feedbackRepository
				.findAllByDatasetIdAndOrganizationIdOrderById(dataset.getId(), organization.getId());
		Long failingFeedbackId = feedbacks.get(0).getId();
		when(aiWorkerClient.analyze(any())).thenAnswer(invocation -> {
			AnalysisRequest request = invocation.getArgument(0);
			if (request.feedbackId().equals(failingFeedbackId)) {
				throw new AiWorkerException("AI Worker request failed");
			}
			return successfulResult(request);
		});

		String responseBody = mockMvc.perform(post(
						"/api/v1/datasets/{datasetId}/analyze",
						dataset.getId()
				)
					.header("Authorization", bearer(admin)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String jobId = objectMapper.readTree(responseBody).path("data").path("jobId").stringValue();

		AnalysisJob completed = awaitTerminalJob(jobId);
		List<AnalysisJobItem> items = itemRepository.findAllByJobIdOrderById(jobId);

		assertThat(completed.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED_WITH_ERRORS);
		assertThat(completed.getProcessedCount()).isEqualTo(2);
		assertThat(completed.getSuccessCount()).isEqualTo(1);
		assertThat(completed.getFailedCount()).isEqualTo(1);
		assertThat(datasetRepository.findById(dataset.getId()).orElseThrow().getStatus())
				.isEqualTo(DatasetStatus.FAILED);
		assertThat(items.get(0).getStatus()).isEqualTo(AnalysisJobItemStatus.FAILED);
		assertThat(items.get(0).getAttemptCount()).isEqualTo(2);
		assertThat(items.get(0).getLastError()).isEqualTo("AI Worker request failed");
		assertThat(analysisRepository.findByFeedbackIdAndFeedbackOrganizationId(
				failingFeedbackId,
				organization.getId()
		).orElseThrow().getStatus()).isEqualTo(AnalysisStatus.FAILED);
		verify(aiWorkerClient, times(3)).analyze(any());
	}

	@Test
	void skipsFeedbackThatAlreadyHasSuccessfulAnalysis() throws Exception {
		Dataset dataset = validatedDataset(organization, admin, 2);
		List<Feedback> feedbacks = feedbackRepository
				.findAllByDatasetIdAndOrganizationIdOrderById(dataset.getId(), organization.getId());
		Feedback alreadyAnalyzed = feedbacks.get(0);
		analysisService.startAnalysis(
				organization.getId(),
				alreadyAnalyzed.getId(),
				"deterministic-v1"
		);
		analysisService.completeAnalysis(
				organization.getId(),
				alreadyAnalyzed.getId(),
				new FeedbackAnalysisService.AnalysisResult(
						Sentiment.POSITIVE,
						BigDecimal.valueOf(0.8),
						"PRODUCT",
						BigDecimal.valueOf(0.1),
						"기존 분석 결과",
						BigDecimal.valueOf(0.9),
						"deterministic-v1"
				)
		);
		when(aiWorkerClient.analyze(any())).thenAnswer(invocation ->
				successfulResult(invocation.getArgument(0))
		);

		String responseBody = mockMvc.perform(post(
						"/api/v1/datasets/{datasetId}/analyze",
						dataset.getId()
				)
					.header("Authorization", bearer(admin)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
		String jobId = objectMapper.readTree(responseBody).path("data").path("jobId").stringValue();

		AnalysisJob completed = awaitTerminalJob(jobId);

		assertThat(completed.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
		assertThat(completed.getSuccessCount()).isEqualTo(2);
		assertThat(analysisRepository.count()).isEqualTo(2);
		verify(aiWorkerClient).analyze(any());
	}

	@Test
	void recoversInterruptedRunningItemWithoutDuplicatingAnalysis() throws Exception {
		Dataset dataset = validatedDataset(organization, admin, 1);
		Feedback feedback = feedbackRepository
				.findAllByDatasetIdAndOrganizationIdOrderById(dataset.getId(), organization.getId())
				.get(0);
		dataset.startAnalysis();
		datasetRepository.saveAndFlush(dataset);
		AnalysisJob job = jobRepository.saveAndFlush(new AnalysisJob(
				organization,
				dataset,
				1
		));
		job.start();
		jobRepository.saveAndFlush(job);
		AnalysisJobItem item = new AnalysisJobItem(job, feedback);
		item.startAttempt();
		itemRepository.saveAndFlush(item);
		analysisService.startAnalysis(
				organization.getId(),
				feedback.getId(),
				"deterministic-v1"
		);
		when(aiWorkerClient.analyze(any())).thenAnswer(invocation ->
				successfulResult(invocation.getArgument(0))
		);

		jobRecovery.recoverActiveJobs();
		AnalysisJob recovered = awaitTerminalJob(job.getId());

		assertThat(recovered.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
		assertThat(recovered.getProcessedCount()).isEqualTo(1);
		AnalysisJobItem recoveredItem = itemRepository.findById(item.getId()).orElseThrow();
		assertThat(recoveredItem.getStatus()).isEqualTo(AnalysisJobItemStatus.SUCCESS);
		assertThat(recoveredItem.getAttemptCount()).isEqualTo(2);
		assertThat(analysisRepository.count()).isEqualTo(1);
		assertThat(analysisRepository.findAll().get(0).getStatus())
				.isEqualTo(AnalysisStatus.SUCCESS);
		verify(aiWorkerClient).analyze(any());
	}

	private Dataset validatedDataset(
			Organization targetOrganization,
			User creator,
			int feedbackCount
	) {
		Dataset dataset = new Dataset(
				targetOrganization,
				"Analysis dataset " + System.nanoTime(),
				SourceType.APP_REVIEW,
				creator
		);
		dataset.startValidation();
		datasetRepository.saveAndFlush(dataset);

		for (int index = 0; index < feedbackCount; index++) {
			feedbackRepository.save(new Feedback(
					targetOrganization,
					dataset,
					"feedback-" + dataset.getId() + "-" + index,
					SourceType.APP_REVIEW,
					"신규 고객",
					"모바일 앱",
					BigDecimal.ONE,
					"쿠폰 적용 후 결제가 안 돼요. " + index,
					"ko",
					null
			));
		}
		feedbackRepository.flush();
		dataset.completeValidation(feedbackCount, feedbackCount, 0);
		return datasetRepository.saveAndFlush(dataset);
	}

	private AnalysisResult successfulResult(AnalysisRequest request) {
		return new AnalysisResult(
				request.feedbackId(),
				Sentiment.NEGATIVE,
				BigDecimal.valueOf(-0.9),
				"PAYMENT",
				BigDecimal.valueOf(0.8),
				"쿠폰 적용 후 결제 실패",
				BigDecimal.valueOf(0.9),
				"deterministic-v1"
		);
	}

	private AnalysisJob awaitLatestJob(Long datasetId) throws InterruptedException {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
		while (Instant.now().isBefore(deadline)) {
			AnalysisJob job = jobRepository
					.findTopByDatasetIdAndOrganizationIdOrderByCreatedAtDesc(
							datasetId,
							organization.getId()
					)
					.orElseThrow();
			if (!job.getStatus().isActive()) {
				return job;
			}
			Thread.sleep(25);
		}
		throw new AssertionError("analysis job did not finish in time");
	}

	private AnalysisJob awaitTerminalJob(String jobId) throws InterruptedException {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
		while (Instant.now().isBefore(deadline)) {
			AnalysisJob job = jobRepository.findById(jobId).orElseThrow();
			if (!job.getStatus().isActive()) {
				return job;
			}
			Thread.sleep(25);
		}
		throw new AssertionError("analysis job did not finish in time");
	}

	private User user(Organization targetOrganization, String email, Role role) {
		return new User(targetOrganization, email, "encoded-password", email, role);
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenProvider.issue(user).value();
	}
}
