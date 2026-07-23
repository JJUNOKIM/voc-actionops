package com.vocactionops.backend.issue.application;

import com.vocactionops.backend.action.application.ActionView;
import com.vocactionops.backend.action.repository.ActionRepository;
import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.common.response.PageResponse;
import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.feedback.domain.Feedback;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.issue.domain.Issue;
import com.vocactionops.backend.issue.domain.IssueFeedback;
import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.domain.LinkSource;
import com.vocactionops.backend.issue.domain.Priority;
import com.vocactionops.backend.issue.repository.IssueFeedbackRepository;
import com.vocactionops.backend.issue.repository.IssueRepository;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.vocactionops.backend.common.web.PageRequestFactory.newestFirst;

@Service
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public class IssueService {

	private final OrganizationRepository organizationRepository;
	private final UserRepository userRepository;
	private final FeedbackRepository feedbackRepository;
	private final IssueRepository issueRepository;
	private final IssueFeedbackRepository issueFeedbackRepository;
	private final ActionRepository actionRepository;
	private final IssuePriorityScoringService priorityScoringService;

	public IssueService(
			OrganizationRepository organizationRepository,
			UserRepository userRepository,
			FeedbackRepository feedbackRepository,
			IssueRepository issueRepository,
			IssueFeedbackRepository issueFeedbackRepository,
			ActionRepository actionRepository,
			IssuePriorityScoringService priorityScoringService
	) {
		this.organizationRepository = organizationRepository;
		this.userRepository = userRepository;
		this.feedbackRepository = feedbackRepository;
		this.issueRepository = issueRepository;
		this.issueFeedbackRepository = issueFeedbackRepository;
		this.actionRepository = actionRepository;
		this.priorityScoringService = priorityScoringService;
	}

	@Transactional
	@PreAuthorize("hasAnyRole('ADMIN', 'PM')")
	public IssueDetail createIssue(
			AuthenticatedUser authenticatedUser,
			String title,
			String description,
			String category,
			Priority priority,
			Long assigneeId
	) {
		Organization organization = organizationRepository.findById(authenticatedUser.organizationId())
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		User assignee = assigneeId == null ? null : getUser(authenticatedUser, assigneeId);
		try {
			Issue issue = issueRepository.save(new Issue(
					organization,
					title,
					description,
					category,
					priority,
					assignee
			));
			return detail(issue);
		} catch (IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	public PageResponse<IssueSummary> getIssues(
			AuthenticatedUser authenticatedUser,
			IssueStatus status,
			Priority priority,
			String category,
			Long assigneeId,
			String keyword,
			LocalDate from,
			LocalDate to,
			int page,
			int size
	) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
		LocalDateTime fromDate = from == null ? null : from.atStartOfDay();
		LocalDateTime toDateExclusive = to == null ? null : to.plusDays(1).atStartOfDay();
		return PageResponse.from(issueRepository.findPageByOrganization(
				authenticatedUser.organizationId(),
				status,
				priority,
				normalizeFilter(category),
				assigneeId,
				normalizeFilter(keyword),
				fromDate,
				toDateExclusive,
				newestFirst(page, size, "createdAt")
		));
	}

	public IssueDetail getIssue(AuthenticatedUser authenticatedUser, Long issueId) {
		return detail(getIssueEntity(authenticatedUser, issueId));
	}

	@Transactional
	@PreAuthorize("hasAnyRole('ADMIN', 'PM')")
	public IssueDetail assignIssue(
			AuthenticatedUser authenticatedUser,
			Long issueId,
			Long assigneeId
	) {
		Issue issue = getIssueEntity(authenticatedUser, issueId);
		try {
			issue.assignTo(getUser(authenticatedUser, assigneeId));
			return detail(issue);
		} catch (IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		} catch (IllegalStateException exception) {
			throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
		}
	}

	@Transactional
	@PreAuthorize("hasAnyRole('ADMIN', 'PM', 'DEVELOPER')")
	public IssueDetail changeStatus(
			AuthenticatedUser authenticatedUser,
			Long issueId,
			IssueStatus status
	) {
		Issue issue = getIssueEntity(authenticatedUser, issueId);
		if (!canChangeStatus(authenticatedUser, issue)) {
			throw new CustomException(ErrorCode.FORBIDDEN);
		}
		try {
			issue.changeStatus(status);
			return detail(issue);
		} catch (IllegalStateException exception) {
			throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
		} catch (IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	@Transactional
	@PreAuthorize("hasAnyRole('ADMIN', 'PM', 'CS')")
	public IssueFeedbackView linkFeedback(
			AuthenticatedUser authenticatedUser,
			Long feedbackId,
			Long issueId,
			boolean representative
	) {
		Issue issue = issueRepository.findByIdAndOrganizationIdForUpdate(
				issueId,
				authenticatedUser.organizationId()
		).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		Feedback feedback = feedbackRepository.findByIdAndOrganizationId(
					feedbackId,
					authenticatedUser.organizationId()
			)
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
		if (issueFeedbackRepository.existsByIssueIdAndFeedbackId(issueId, feedbackId)) {
			throw new CustomException(ErrorCode.DUPLICATED_RESOURCE);
		}
		try {
			IssueFeedback link = issueFeedbackRepository.save(new IssueFeedback(
					issue,
					feedback,
					null,
					representative,
					LinkSource.MANUAL
			));
			priorityScoringService.recalculate(authenticatedUser.organizationId(), issueId);
			return IssueFeedbackView.from(link);
		} catch (IllegalArgumentException exception) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}
	}

	public PageResponse<IssueFeedbackView> getIssueFeedbacks(
			AuthenticatedUser authenticatedUser,
			Long issueId,
			boolean representativeOnly,
			int page,
			int size
	) {
		getIssueEntity(authenticatedUser, issueId);
		return PageResponse.from(issueFeedbackRepository.findPageByIssueAndOrganization(
				issueId,
				authenticatedUser.organizationId(),
				representativeOnly,
				newestFirst(page, size, "createdAt")
		).map(IssueFeedbackView::from));
	}

	private Issue getIssueEntity(AuthenticatedUser authenticatedUser, Long issueId) {
		return issueRepository.findByIdAndOrganizationId(issueId, authenticatedUser.organizationId())
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
	}

	private User getUser(AuthenticatedUser authenticatedUser, Long userId) {
		return userRepository.findByIdAndOrganizationId(userId, authenticatedUser.organizationId())
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
	}

	private boolean canChangeStatus(AuthenticatedUser authenticatedUser, Issue issue) {
		if (authenticatedUser.role() == Role.ADMIN || authenticatedUser.role() == Role.PM) {
			return true;
		}
		User assignee = issue.getAssignee();
		return assignee != null && assignee.getId().equals(authenticatedUser.userId());
	}

	private IssueDetail detail(Issue issue) {
		User assignee = issue.getAssignee();
		List<ActionView> actions = actionRepository
				.findAllByIssueIdAndIssueOrganizationIdOrderByCreatedAtDesc(
						issue.getId(),
						issue.getOrganization().getId()
				)
				.stream()
				.map(ActionView::from)
				.toList();
		return new IssueDetail(
				issue.getId(),
				issue.getTitle(),
				issue.getDescription(),
				issue.getCategory(),
				issue.getPriority(),
				issue.getPriorityScore(),
				issue.getStatus(),
				assignee == null ? null : assignee.getId(),
				assignee == null ? null : assignee.getName(),
				issueFeedbackRepository.countByIssueId(issue.getId()),
				issueFeedbackRepository.countNegativeByIssueId(issue.getId()),
				issue.getFirstSeenAt(),
				issue.getLastSeenAt(),
				issue.getCreatedAt(),
				issue.getUpdatedAt(),
				actions
		);
	}

	private static String normalizeFilter(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public record IssueDetail(
			Long id,
			String title,
			String description,
			String category,
			Priority priority,
			BigDecimal priorityScore,
			IssueStatus status,
			Long assigneeId,
			String assigneeName,
			long feedbackCount,
			long negativeCount,
			LocalDateTime firstSeenAt,
			LocalDateTime lastSeenAt,
			LocalDateTime createdAt,
			LocalDateTime updatedAt,
			List<ActionView> actions
	) {
	}

	public record IssueFeedbackView(
			Long id,
			Long feedbackId,
			Long datasetId,
			String externalId,
			SourceType sourceType,
			String content,
			BigDecimal rating,
			BigDecimal similarityScore,
			boolean representative,
			LinkSource linkedBy,
			LocalDateTime feedbackCreatedAt,
			LocalDateTime linkedAt
	) {
		private static IssueFeedbackView from(IssueFeedback link) {
			Feedback feedback = link.getFeedback();
			return new IssueFeedbackView(
					link.getId(),
					feedback.getId(),
					feedback.getDataset().getId(),
					feedback.getExternalId(),
					feedback.getSourceType(),
					feedback.getContent(),
					feedback.getRating(),
					link.getSimilarityScore(),
					link.isRepresentative(),
					link.getLinkedBy(),
					feedback.getFeedbackCreatedAt(),
					link.getCreatedAt()
			);
		}
	}
}
