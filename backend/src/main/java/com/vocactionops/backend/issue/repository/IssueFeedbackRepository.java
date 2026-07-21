package com.vocactionops.backend.issue.repository;

import com.vocactionops.backend.issue.domain.IssueFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueFeedbackRepository extends JpaRepository<IssueFeedback, Long> {

	boolean existsByIssueIdAndFeedbackId(Long issueId, Long feedbackId);

	long countByIssueId(Long issueId);

	@Query("""
			SELECT COUNT(link)
			FROM IssueFeedback link
			JOIN FeedbackAnalysis analysis ON analysis.feedback = link.feedback
			WHERE link.issue.id = :issueId
			  AND analysis.sentiment = com.vocactionops.backend.analysis.domain.Sentiment.NEGATIVE
			""")
	long countNegativeByIssueId(@Param("issueId") Long issueId);

	@Query(
			value = """
					SELECT link
					FROM IssueFeedback link
					JOIN FETCH link.feedback feedback
					JOIN FETCH feedback.dataset
					WHERE link.issue.id = :issueId
					  AND link.issue.organization.id = :organizationId
					  AND (:representativeOnly = false OR link.representative = true)
					""",
			countQuery = """
					SELECT COUNT(link)
					FROM IssueFeedback link
					WHERE link.issue.id = :issueId
					  AND link.issue.organization.id = :organizationId
					  AND (:representativeOnly = false OR link.representative = true)
					"""
	)
	Page<IssueFeedback> findPageByIssueAndOrganization(
			@Param("issueId") Long issueId,
			@Param("organizationId") Long organizationId,
			@Param("representativeOnly") boolean representativeOnly,
			Pageable pageable
	);
}
