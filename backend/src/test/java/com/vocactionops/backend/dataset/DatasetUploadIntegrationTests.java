package com.vocactionops.backend.dataset;

import com.vocactionops.backend.analysis.repository.AiCorrectionRepository;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.auth.token.JwtTokenProvider;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dataset.config.DatasetUploadProperties;
import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.domain.DatasetStatus;
import com.vocactionops.backend.dataset.domain.DatasetValidationErrorCode;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.dataset.repository.DatasetValidationErrorRepository;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatasetUploadIntegrationTests {

	private static final Path UPLOAD_DIRECTORY = createUploadDirectory();

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
	private DatasetValidationErrorRepository validationErrorRepository;

	@Autowired
	private FeedbackAnalysisRepository analysisRepository;

	@Autowired
	private AiCorrectionRepository correctionRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private DatasetUploadProperties uploadProperties;

	private Organization organization;
	private User admin;
	private User csUser;
	private User otherAdmin;

	@DynamicPropertySource
	static void datasetUploadProperties(DynamicPropertyRegistry registry) {
		registry.add("app.dataset-upload.storage-directory", UPLOAD_DIRECTORY::toString);
	}

	@BeforeEach
	void setUp() throws IOException {
		correctionRepository.deleteAll();
		analysisRepository.deleteAll();
		validationErrorRepository.deleteAll();
		feedbackRepository.deleteAll();
		datasetRepository.deleteAll();
		userRepository.deleteAll();
		organizationRepository.deleteAll();
		clearUploadDirectory();

		organization = organizationRepository.save(new Organization("VOC Team"));
		Organization otherOrganization = organizationRepository.save(new Organization("Other Team"));
		admin = userRepository.save(user(organization, "admin@example.com", Role.ADMIN));
		csUser = userRepository.save(user(organization, "cs@example.com", Role.CS));
		otherAdmin = userRepository.save(user(otherOrganization, "other@example.com", Role.ADMIN));
	}

	@AfterAll
	static void cleanUpUploadDirectory() throws IOException {
		deleteRecursively(UPLOAD_DIRECTORY);
	}

	@Test
	void uploadsValidRowsAndPersistsRowErrors() throws Exception {
		String csv = """
				\uFEFFreview_id,review_text,score,created_date,product
				review-001,Checkout fails,1.0,2026-07-01T12:00:00,Mobile app
				review-002, ,4.0,2026-07-01,Mobile app
				review-003,Slow,6.0,2026-07-01,Mobile app
				review-004,Crash,2.0,not-a-date,Mobile app
				review-001,Duplicate,3.0,2026-07-02,Mobile app
				review-005,Works now,5,2026-07-03,Mobile app
				""";

		String responseBody = mockMvc.perform(uploadRequest(admin, csv, mapping()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("VALIDATED"))
				.andExpect(jsonPath("$.data.totalCount").value(6))
				.andExpect(jsonPath("$.data.validCount").value(2))
				.andExpect(jsonPath("$.data.invalidCount").value(4))
				.andReturn()
				.getResponse()
				.getContentAsString();
		JsonNode response = objectMapper.readTree(responseBody);
		long datasetId = response.path("data").path("datasetId").asLong();

		Dataset dataset = datasetRepository.findById(datasetId).orElseThrow();
		assertThat(dataset.getStatus()).isEqualTo(DatasetStatus.VALIDATED);
		assertThat(dataset.getFileUrl()).startsWith("local://dataset-files/");
		assertThat(dataset.getColumnMapping()).containsEntry("review_text", "content");
		assertThat(feedbackRepository.findAll())
				.extracting(feedback -> feedback.getExternalId())
				.containsExactlyInAnyOrder("review-001", "review-005");
		assertThat(validationErrorRepository.findAll())
				.extracting(error -> error.getErrorCode())
				.containsExactlyInAnyOrder(
						DatasetValidationErrorCode.EMPTY_CONTENT,
						DatasetValidationErrorCode.INVALID_RATING_RANGE,
						DatasetValidationErrorCode.INVALID_DATE_FORMAT,
						DatasetValidationErrorCode.DUPLICATED_EXTERNAL_ID
				);
		assertThat(storedFileCount()).isEqualTo(1);

		mockMvc.perform(get("/api/v1/datasets/{datasetId}/validation-errors", datasetId)
						.header("Authorization", bearer(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(4))
				.andExpect(jsonPath("$.data.content[*].rowNumber", containsInAnyOrder(3, 4, 5, 6)))
				.andExpect(jsonPath("$.data.content[0].rawRow.review_text").value(" "));

		mockMvc.perform(get("/api/v1/datasets/{datasetId}/validation-errors", datasetId)
						.header("Authorization", bearer(otherAdmin)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_FOUND.code()));
	}

	@Test
	void rejectsFileLevelValidationFailureWithoutCreatingDatasetOrFile() throws Exception {
		String csv = "review_text,score\nValid,5\n";
		Map<String, String> invalidMapping = Map.of("missing_header", "content");

		mockMvc.perform(uploadRequest(admin, csv, invalidMapping))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.CSV_VALIDATION_FAILED.code()));

		assertThat(datasetRepository.count()).isZero();
		assertThat(feedbackRepository.count()).isZero();
		assertThat(validationErrorRepository.count()).isZero();
		assertThat(storedFileCount()).isZero();
	}

	@Test
	void allowsOnlyAdminAndPmToUploadDatasets() throws Exception {
		String csv = "review_text\nValid feedback\n";

		mockMvc.perform(uploadRequest(
				csUser,
				csv,
				Map.of("review_text", "content")
		))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.code()));

		assertThat(datasetRepository.count()).isZero();
		assertThat(storedFileCount()).isZero();
	}

	private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder uploadRequest(
			User user,
			String csv,
			Map<String, String> columnMapping
	) throws Exception {
		MockMultipartFile file = new MockMultipartFile(
				"file",
				"reviews.csv",
				"text/csv",
				csv.getBytes(StandardCharsets.UTF_8)
		);
		MockMultipartFile mappingPart = new MockMultipartFile(
				"columnMapping",
				"column-mapping.json",
				MediaType.APPLICATION_JSON_VALUE,
				objectMapper.writeValueAsBytes(columnMapping)
		);
		return multipart("/api/v1/datasets")
				.file(file)
				.file(mappingPart)
				.param("name", "2026 July App Reviews")
				.param("sourceType", "APP_REVIEW")
				.header("Authorization", bearer(user));
	}

	private Map<String, String> mapping() {
		return Map.of(
				"review_id", "external_id",
				"review_text", "content",
				"score", "rating",
				"created_date", "feedback_created_at",
				"product", "product_name"
		);
	}

	private User user(Organization organization, String email, Role role) {
		return new User(organization, email, "encoded-password", email, role);
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenProvider.issue(user).value();
	}

	private long storedFileCount() throws IOException {
		Path root = uploadProperties.storageDirectory();
		if (!Files.exists(root)) {
			return 0;
		}
		try (var paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile).count();
		}
	}

	private static Path createUploadDirectory() {
		try {
			return Files.createTempDirectory("voc-actionops-upload-tests-");
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static void clearUploadDirectory() throws IOException {
		if (!Files.exists(UPLOAD_DIRECTORY)) {
			Files.createDirectories(UPLOAD_DIRECTORY);
			return;
		}
		try (var paths = Files.walk(UPLOAD_DIRECTORY)) {
			paths.filter(path -> !path.equals(UPLOAD_DIRECTORY))
					.sorted(Comparator.reverseOrder())
					.forEach(DatasetUploadIntegrationTests::deleteUnchecked);
		}
	}

	private static void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path)) {
			return;
		}
		try (var paths = Files.walk(path)) {
			paths.sorted(Comparator.reverseOrder())
					.forEach(DatasetUploadIntegrationTests::deleteUnchecked);
		}
	}

	private static void deleteUnchecked(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
