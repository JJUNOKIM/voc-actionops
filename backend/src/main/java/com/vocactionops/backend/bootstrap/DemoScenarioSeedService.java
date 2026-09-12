package com.vocactionops.backend.bootstrap;

import com.vocactionops.backend.action.domain.Action;
import com.vocactionops.backend.action.domain.ActionStatus;
import com.vocactionops.backend.action.repository.ActionRepository;
import com.vocactionops.backend.analysis.domain.FeedbackAnalysis;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.bootstrap.DemoFeedbackFixtures.FeedbackSeed;
import com.vocactionops.backend.dashboard.application.IssueMetricsSnapshotService;
import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.issue.application.IssuePriorityScoringService;
import com.vocactionops.backend.issue.domain.Issue;
import com.vocactionops.backend.issue.domain.IssueFeedback;
import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.domain.LinkSource;
import com.vocactionops.backend.issue.domain.Priority;
import com.vocactionops.backend.issue.repository.IssueFeedbackRepository;
import com.vocactionops.backend.issue.repository.IssueRepository;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.user.domain.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.vocactionops.backend.bootstrap.DemoFeedbackFixtures.feedbackSeeds;

@Service
public class DemoScenarioSeedService {

	static final String DATASET_NAME = "쇼핑앱 VOC 샘플";
	private static final String MODEL_NAME = "deterministic-v1";

	private final DatasetRepository datasetRepository;
	private final FeedbackRepository feedbackRepository;
	private final FeedbackAnalysisRepository analysisRepository;
	private final IssueRepository issueRepository;
	private final IssueFeedbackRepository issueFeedbackRepository;
	private final ActionRepository actionRepository;
	private final IssuePriorityScoringService priorityScoringService;
	private final IssueMetricsSnapshotService snapshotService;

	public DemoScenarioSeedService(
			DatasetRepository datasetRepository,
			FeedbackRepository feedbackRepository,
			FeedbackAnalysisRepository analysisRepository,
			IssueRepository issueRepository,
			IssueFeedbackRepository issueFeedbackRepository,
			ActionRepository actionRepository,
			IssuePriorityScoringService priorityScoringService,
			IssueMetricsSnapshotService snapshotService
	) {
		this.datasetRepository = datasetRepository;
		this.feedbackRepository = feedbackRepository;
		this.analysisRepository = analysisRepository;
		this.issueRepository = issueRepository;
		this.issueFeedbackRepository = issueFeedbackRepository;
		this.actionRepository = actionRepository;
		this.priorityScoringService = priorityScoringService;
		this.snapshotService = snapshotService;
	}

	public void initialize(User admin) {
		Organization organization = admin.getOrganization();
		if (datasetRepository.existsByOrganizationIdAndName(
				organization.getId(),
				DATASET_NAME
		)) {
			return;
		}

		LocalDate today = snapshotService.currentDate();
		List<FeedbackSeed> seeds = feedbackSeeds();
		Dataset dataset = createDataset(organization, admin, seeds.size());
		List<Feedback> feedbacks = createFeedbacks(organization, dataset, today, seeds);
		createAnalyses(feedbacks, seeds);
		List<Issue> issues = createIssues(organization, admin);
		linkFeedbacks(issues, feedbacks);
		for (Issue issue : issues) {
			priorityScoringService.recalculate(organization.getId(), issue.getId());
		}
		createActions(issues, admin, today);
		actionRepository.flush();
		snapshotService.captureOrganization(organization.getId(), today);
	}

	private Dataset createDataset(Organization organization, User admin, int feedbackCount) {
		Dataset dataset = new Dataset(
				organization,
				DATASET_NAME,
				SourceType.APP_REVIEW,
				null,
				Map.of(
						"external_id", "external_id",
						"content", "content",
						"rating", "rating",
						"feedback_created_at", "feedback_created_at"
				),
				admin
		);
		dataset.startValidation();
		dataset.completeValidation(feedbackCount, feedbackCount, 0);
		dataset.startAnalysis();
		dataset.completeAnalysis(false);
		return datasetRepository.save(dataset);
	}

	private List<Feedback> createFeedbacks(
			Organization organization,
			Dataset dataset,
			LocalDate today,
			List<FeedbackSeed> seeds
	) {
		List<Feedback> feedbacks = seeds.stream()
				.map(seed -> new Feedback(
						organization,
						dataset,
						seed.externalId(),
						SourceType.APP_REVIEW,
						seed.customerSegment(),
						"쇼핑앱",
						seed.rating(),
						seed.content(),
						"ko",
						occurredAt(today, seed.daysAgo(), seed.hour(), seed.minute())
				))
				.toList();
		return feedbackRepository.saveAllAndFlush(feedbacks);
	}

	private void createAnalyses(List<Feedback> feedbacks, List<FeedbackSeed> seeds) {
		List<FeedbackAnalysis> analyses = new ArrayList<>(feedbacks.size());
		for (int index = 0; index < feedbacks.size(); index++) {
			FeedbackSeed seed = seeds.get(index);
			FeedbackAnalysis analysis = new FeedbackAnalysis(feedbacks.get(index), MODEL_NAME);
			analysis.complete(
					seed.sentiment(),
					seed.sentimentScore(),
					seed.category(),
					seed.urgencyScore(),
					seed.summary(),
					seed.confidenceScore()
			);
			analyses.add(analysis);
		}
		analysisRepository.saveAllAndFlush(analyses);
	}

	private List<Issue> createIssues(Organization organization, User admin) {
		Issue payment = new Issue(
				organization,
				"쿠폰 결제 실패 및 중복 승인",
				"쿠폰을 적용한 결제에서 주문 생성 실패, 포인트 차감, 중복 승인 사례가 반복되고 있습니다.",
				"PAYMENT",
				Priority.P3,
				admin
		);
		payment.changeStatus(IssueStatus.TRIAGED);
		payment.changeStatus(IssueStatus.ASSIGNED);
		payment.changeStatus(IssueStatus.IN_PROGRESS);

		Issue account = new Issue(
				organization,
				"로그인 인증 실패",
				"인증 문자 미수신과 비밀번호 변경 후 로그인 실패 사례를 함께 점검해야 합니다.",
				"ACCOUNT",
				Priority.P3,
				admin
		);
		account.changeStatus(IssueStatus.TRIAGED);
		account.changeStatus(IssueStatus.ASSIGNED);

		Issue delivery = new Issue(
				organization,
				"배송 조회 정보 지연",
				"배송 예정일과 현재 위치 정보가 늦게 반영된다는 문의가 접수되고 있습니다.",
				"DELIVERY",
				Priority.P3,
				null
		);
		delivery.changeStatus(IssueStatus.TRIAGED);

		return issueRepository.saveAllAndFlush(List.of(payment, account, delivery));
	}

	private void linkFeedbacks(List<Issue> issues, List<Feedback> feedbacks) {
		List<IssueFeedback> links = new ArrayList<>();
		for (int index = 0; index < 8; index++) {
			links.add(new IssueFeedback(
					issues.get(0),
					feedbacks.get(index),
					decimal(index == 0 ? "0.9820" : "0.9100"),
					index == 0,
					LinkSource.AI
			));
		}
		links.add(new IssueFeedback(
				issues.get(1), feedbacks.get(8), decimal("0.9670"), true, LinkSource.AI
		));
		links.add(new IssueFeedback(
				issues.get(1), feedbacks.get(9), decimal("0.9340"), false, LinkSource.AI
		));
		links.add(new IssueFeedback(
				issues.get(2), feedbacks.get(10), decimal("0.9530"), true, LinkSource.AI
		));
		links.add(new IssueFeedback(
				issues.get(2), feedbacks.get(11), decimal("0.9010"), false, LinkSource.AI
		));
		issueFeedbackRepository.saveAllAndFlush(links);
	}

	private void createActions(List<Issue> issues, User admin, LocalDate today) {
		Action reproducePayment = new Action(
				issues.get(0),
				"쿠폰별 결제 실패 조건 재현",
				"최근 제보에 포함된 쿠폰과 결제 수단 조합을 우선 확인합니다.",
				admin,
				today.plusDays(2)
		);
		reproducePayment.changeStatus(ActionStatus.IN_PROGRESS);

		Action restorePoints = new Action(
				issues.get(0),
				"포인트 차감 대상 확인",
				"주문 생성에 실패했지만 포인트가 차감된 고객 목록을 추출합니다.",
				admin,
				today.plusDays(1)
		);
		Action inspectLogin = new Action(
				issues.get(1),
				"인증 문자 발송 로그 점검",
				null,
				admin,
				today.plusDays(3)
		);
		actionRepository.saveAll(List.of(reproducePayment, restorePoints, inspectLogin));
	}

	private static LocalDateTime occurredAt(
			LocalDate today,
			int daysAgo,
			int hour,
			int minute
	) {
		return today.minusDays(daysAgo).atTime(hour, minute);
	}

	private static BigDecimal decimal(String value) {
		return new BigDecimal(value);
	}
}
