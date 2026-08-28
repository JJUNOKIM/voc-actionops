package com.vocactionops.backend.analysis.repository;

import com.vocactionops.backend.analysis.domain.FeedbackAnalysis;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FeedbackAnalysisRepository extends JpaRepository<FeedbackAnalysis, Long> {

	@Query("""
			SELECT analysis
			FROM FeedbackAnalysis analysis
			WHERE analysis.feedback.id IN :feedbackIds
			  AND analysis.feedback.organization.id = :organizationId
			""")
	List<FeedbackAnalysis> findAllByFeedbackIdsAndOrganization(
			@Param("feedbackIds") Collection<Long> feedbackIds,
			@Param("organizationId") Long organizationId
	);

	Optional<FeedbackAnalysis> findByFeedbackIdAndFeedbackOrganizationId(
			Long feedbackId,
			Long organizationId
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT analysis
			FROM FeedbackAnalysis analysis
			WHERE analysis.feedback.id = :feedbackId
			  AND analysis.feedback.organization.id = :organizationId
			""")
	Optional<FeedbackAnalysis> findByFeedbackAndOrganizationForUpdate(
			@Param("feedbackId") Long feedbackId,
			@Param("organizationId") Long organizationId
	);
}
