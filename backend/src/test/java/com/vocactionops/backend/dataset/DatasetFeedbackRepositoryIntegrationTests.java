package com.vocactionops.backend.dataset;

import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.domain.DatasetStatus;
import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class DatasetFeedbackRepositoryIntegrationTests {

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DatasetRepository datasetRepository;

	@Autowired
	private FeedbackRepository feedbackRepository;

	@Autowired
	private EntityManager entityManager;

	private Organization organization;
	private User creator;

	@BeforeEach
	void setUp() {
		organization = organizationRepository.save(new Organization("VOC Team"));
		creator = userRepository.save(new User(
				organization,
				"pm@example.com",
				"encoded-password",
				"PM User",
				Role.PM
		));
	}

	@Test
	void savesDatasetAndFeedbackWithStructuredMetadata() {
		Dataset dataset = datasetRepository.saveAndFlush(new Dataset(
				organization,
				"2026 July App Reviews",
				SourceType.APP_REVIEW,
				"s3://voc-actionops/reviews.csv",
				Map.of("review_text", "content", "score", "rating"),
				creator
		));
		Feedback feedback = feedbackRepository.saveAndFlush(new Feedback(
				organization,
				dataset,
				"review-001",
				SourceType.APP_REVIEW,
				"new-customer",
				"mobile-app",
				new BigDecimal("1.0"),
				"Coupon payment failed.",
				"ko",
				LocalDateTime.of(2026, 7, 1, 12, 0)
		));

		entityManager.clear();

		Dataset savedDataset = datasetRepository.findById(dataset.getId()).orElseThrow();
		Feedback savedFeedback = feedbackRepository.findById(feedback.getId()).orElseThrow();

		assertThat(savedDataset.getColumnMapping()).containsEntry("review_text", "content");
		assertThat(savedDataset.getStatus()).isEqualTo(DatasetStatus.UPLOADED);
		assertThat(savedDataset.getCreatedAt()).isNotNull();
		assertThat(savedFeedback.getDataset().getId()).isEqualTo(savedDataset.getId());
		assertThat(savedFeedback.getRating()).isEqualByComparingTo("1.0");
		assertThat(savedFeedback.getIngestedAt()).isNotNull();
	}

	@Test
	void enforcesDatasetValidationCountInvariant() {
		Dataset dataset = new Dataset(organization, "CS Tickets", SourceType.CS_TICKET, creator);

		dataset.startValidation();

		assertThatThrownBy(() -> dataset.completeValidation(10, 8, 1))
				.isInstanceOf(IllegalArgumentException.class);

		dataset.completeValidation(10, 8, 2);
		datasetRepository.saveAndFlush(dataset);

		assertThat(dataset.getStatus()).isEqualTo(DatasetStatus.VALIDATED);
		assertThat(dataset.getTotalCount()).isEqualTo(10);
		assertThat(dataset.getValidCount()).isEqualTo(8);
		assertThat(dataset.getInvalidCount()).isEqualTo(2);
	}

	@Test
	void rejectsRelationshipsAcrossOrganizations() {
		Organization otherOrganization = organizationRepository.save(new Organization("Other Team"));
		User otherUser = userRepository.save(new User(
				otherOrganization,
				"other@example.com",
				"encoded-password",
				"Other User",
				Role.PM
		));
		Dataset dataset = datasetRepository.save(new Dataset(
				organization,
				"App Reviews",
				SourceType.APP_REVIEW,
				creator
		));

		assertThatThrownBy(() -> new Dataset(
				organization,
				"Invalid Dataset",
				SourceType.SURVEY,
				otherUser
		)).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new Feedback(
				otherOrganization,
				dataset,
				"invalid-001",
				SourceType.APP_REVIEW,
				null,
				null,
				null,
				"Invalid relationship",
				null,
				null
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsDuplicateExternalIdWithinDataset() {
		Dataset dataset = datasetRepository.save(new Dataset(
				organization,
				"Survey",
				SourceType.SURVEY,
				creator
		));
		feedbackRepository.saveAndFlush(feedback(dataset, "response-001", "First response"));

		assertThatThrownBy(() -> feedbackRepository.saveAndFlush(
				feedback(dataset, "response-001", "Duplicate response")
		)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private Feedback feedback(Dataset dataset, String externalId, String content) {
		return new Feedback(
				organization,
				dataset,
				externalId,
				SourceType.SURVEY,
				null,
				null,
				new BigDecimal("4.0"),
				content,
				"ko",
				LocalDateTime.now()
		);
	}
}
