package com.vocactionops.backend.issue.repository;

import com.vocactionops.backend.issue.application.IssueSummary;
import com.vocactionops.backend.issue.domain.Issue;
import com.vocactionops.backend.issue.domain.IssueStatus;
import com.vocactionops.backend.issue.domain.Priority;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

	@EntityGraph(attributePaths = "assignee")
	Optional<Issue> findByIdAndOrganizationId(Long id, Long organizationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT issue
			FROM Issue issue
			WHERE issue.id = :issueId
			  AND issue.organization.id = :organizationId
			""")
	Optional<Issue> findByIdAndOrganizationIdForUpdate(
			@Param("issueId") Long issueId,
			@Param("organizationId") Long organizationId
	);

	@Query(
			value = """
					SELECT new com.vocactionops.backend.issue.application.IssueSummary(
						issue.id,
						issue.title,
						issue.category,
						issue.priority,
						issue.priorityScore,
						issue.status,
						assignee.id,
						assignee.name,
						COUNT(link.id),
						COALESCE(SUM(CASE WHEN analysis.sentiment = com.vocactionops.backend.analysis.domain.Sentiment.NEGATIVE THEN 1 ELSE 0 END), 0),
						issue.firstSeenAt,
						issue.lastSeenAt,
						issue.createdAt,
						issue.updatedAt
					)
					FROM Issue issue
					LEFT JOIN issue.assignee assignee
					LEFT JOIN IssueFeedback link ON link.issue = issue
					LEFT JOIN FeedbackAnalysis analysis ON analysis.feedback = link.feedback
					WHERE issue.organization.id = :organizationId
					  AND (:status IS NULL OR issue.status = :status)
					  AND (:priority IS NULL OR issue.priority = :priority)
					  AND (:category IS NULL OR issue.category = :category)
					  AND (:assigneeId IS NULL OR assignee.id = :assigneeId)
					  AND (:keyword IS NULL
					       OR LOWER(issue.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
					       OR LOWER(issue.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
					  AND (:fromDate IS NULL OR issue.createdAt >= :fromDate)
					  AND (:toDateExclusive IS NULL OR issue.createdAt < :toDateExclusive)
					GROUP BY issue.id, issue.title, issue.category, issue.priority,
					         issue.priorityScore, issue.status, assignee.id, assignee.name,
					         issue.firstSeenAt, issue.lastSeenAt, issue.createdAt, issue.updatedAt
					""",
			countQuery = """
					SELECT COUNT(issue)
					FROM Issue issue
					LEFT JOIN issue.assignee assignee
					WHERE issue.organization.id = :organizationId
					  AND (:status IS NULL OR issue.status = :status)
					  AND (:priority IS NULL OR issue.priority = :priority)
					  AND (:category IS NULL OR issue.category = :category)
					  AND (:assigneeId IS NULL OR assignee.id = :assigneeId)
					  AND (:keyword IS NULL
					       OR LOWER(issue.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
					       OR LOWER(issue.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
					  AND (:fromDate IS NULL OR issue.createdAt >= :fromDate)
					  AND (:toDateExclusive IS NULL OR issue.createdAt < :toDateExclusive)
					"""
	)
	Page<IssueSummary> findPageByOrganization(
			@Param("organizationId") Long organizationId,
			@Param("status") IssueStatus status,
			@Param("priority") Priority priority,
			@Param("category") String category,
			@Param("assigneeId") Long assigneeId,
			@Param("keyword") String keyword,
			@Param("fromDate") LocalDateTime fromDate,
			@Param("toDateExclusive") LocalDateTime toDateExclusive,
			Pageable pageable
	);
}
