package com.vocactionops.backend.bootstrap;

import com.vocactionops.backend.action.domain.ActionStatus;
import com.vocactionops.backend.action.repository.ActionRepository;
import com.vocactionops.backend.analysis.job.domain.AnalysisJobItemStatus;
import com.vocactionops.backend.analysis.job.domain.AnalysisJobStatus;
import com.vocactionops.backend.analysis.job.repository.AnalysisJobItemRepository;
import com.vocactionops.backend.analysis.job.repository.AnalysisJobRepository;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.dashboard.application.IssueMetricsSnapshotService;
import com.vocactionops.backend.dashboard.repository.IssueMetricsSnapshotRepository;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.domain.Priority;
import com.vocactionops.backend.issue.repository.IssueFeedbackRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DemoDataBootstrapIntegrationTests {

	@Autowired
	private DemoDataBootstrapService bootstrapService;

	@Autowired
	private DemoDataProperties properties;

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
	private AnalysisJobRepository analysisJobRepository;

	@Autowired
	private AnalysisJobItemRepository analysisJobItemRepository;

	@Autowired
	private IssueRepository issueRepository;

	@Autowired
	private IssueFeedbackRepository issueFeedbackRepository;

	@Autowired
	private ActionRepository actionRepository;

	@Autowired
	private IssueMetricsSnapshotRepository snapshotRepository;

	@Autowired
	private IssueMetricsSnapshotService snapshotService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private DatabaseCleaner databaseCleaner;

	@BeforeEach
	void setUp() {
		databaseCleaner.clean();
	}

	@Test
	void createsCompleteDemoScenarioOnlyOnce() {
		bootstrapService.initialize();
		bootstrapService.initialize();

		assertThat(organizationRepository.count()).isOne();
		assertThat(userRepository.count()).isEqualTo(5);

		User user = userRepository.findByEmailIgnoreCase(properties.userEmail()).orElseThrow();
		assertThat(organizationRepository.findAll())
				.singleElement()
				.extracting(organization -> organization.getName())
				.isEqualTo(properties.organizationName());
		assertThat(user.getName()).isEqualTo(properties.userName());
		assertThat(user.getRole()).isEqualTo(Role.ADMIN);
		assertThat(passwordEncoder.matches(properties.userPassword(), user.getPasswordHash())).isTrue();
		assertThat(user.getPasswordHash()).isNotEqualTo(properties.userPassword());
		assertThat(userRepository.findAll())
				.extracting(User::getRole)
				.containsExactlyInAnyOrder(
						Role.ADMIN,
						Role.PM,
						Role.CS,
						Role.DEVELOPER,
						Role.VIEWER
				);

		assertThat(datasetRepository.findAll())
				.singleElement()
				.satisfies(dataset -> {
					assertThat(dataset.getName()).isEqualTo(DemoScenarioSeedService.DATASET_NAME);
					assertThat(dataset.getTotalCount()).isEqualTo(14);
					assertThat(dataset.getValidCount()).isEqualTo(14);
				});
		assertThat(feedbackRepository.count()).isEqualTo(14);
		assertThat(analysisRepository.count()).isEqualTo(14);
		assertThat(analysisJobRepository.findAll())
				.singleElement()
				.satisfies(job -> {
					assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
					assertThat(job.getProcessedCount()).isEqualTo(14);
					assertThat(job.getSuccessCount()).isEqualTo(14);
				});
		assertThat(analysisJobItemRepository.findAll())
				.hasSize(14)
				.allSatisfy(item -> assertThat(item.getStatus())
						.isEqualTo(AnalysisJobItemStatus.SUCCESS));
		assertThat(issueRepository.findAll())
				.extracting(issue -> issue.getStatus())
				.containsExactlyInAnyOrder(
						IssueStatus.IN_PROGRESS,
						IssueStatus.ASSIGNED,
						IssueStatus.TRIAGED
				);
		assertThat(issueRepository.findAll())
				.extracting(issue -> issue.getPriority())
				.contains(Priority.P0, Priority.P1);
		assertThat(issueFeedbackRepository.count()).isEqualTo(12);
		assertThat(actionRepository.findAll())
				.extracting(action -> action.getStatus())
				.containsExactlyInAnyOrder(
						ActionStatus.IN_PROGRESS,
						ActionStatus.TODO,
						ActionStatus.TODO
				);
		assertThat(snapshotRepository.findAll())
				.hasSize(3)
				.allSatisfy(snapshot -> assertThat(snapshot.getSnapshotDate())
						.isEqualTo(snapshotService.currentDate()));
	}

	@Test
	void addsScenarioWhenDemoAdminAlreadyExists() {
		Organization organization = organizationRepository.save(
				new Organization(properties.organizationName())
		);
		userRepository.save(new User(
				organization,
				properties.userEmail(),
				passwordEncoder.encode(properties.userPassword()),
				properties.userName(),
				Role.ADMIN
		));

		bootstrapService.initialize();

		assertThat(organizationRepository.count()).isOne();
		assertThat(userRepository.count()).isEqualTo(5);
		assertThat(datasetRepository.count()).isOne();
		assertThat(feedbackRepository.count()).isEqualTo(14);
		assertThat(issueRepository.count()).isEqualTo(3);
	}

	@Test
	void restoresMissingAnalysisJobWithoutDuplicatingScenario() {
		bootstrapService.initialize();
		analysisJobItemRepository.deleteAll();
		analysisJobRepository.deleteAll();

		bootstrapService.initialize();
		bootstrapService.initialize();

		assertThat(datasetRepository.count()).isOne();
		assertThat(feedbackRepository.count()).isEqualTo(14);
		assertThat(issueRepository.count()).isEqualTo(3);
		assertThat(analysisJobRepository.count()).isOne();
		assertThat(analysisJobItemRepository.count()).isEqualTo(14);
	}
}
