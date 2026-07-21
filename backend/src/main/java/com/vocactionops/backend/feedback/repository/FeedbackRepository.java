package com.vocactionops.backend.feedback.repository;

import com.vocactionops.backend.dataset.domain.SourceType;
import com.vocactionops.backend.feedback.domain.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

	@Query("""
			SELECT feedback
			FROM Feedback feedback
			WHERE feedback.organization.id = :organizationId
			  AND (:datasetId IS NULL OR feedback.dataset.id = :datasetId)
			  AND (:sourceType IS NULL OR feedback.sourceType = :sourceType)
			""")
	Page<Feedback> findPageByOrganization(
			@Param("organizationId") Long organizationId,
			@Param("datasetId") Long datasetId,
			@Param("sourceType") SourceType sourceType,
			Pageable pageable
	);

	Optional<Feedback> findByIdAndOrganizationId(Long id, Long organizationId);

	long countByDatasetIdAndOrganizationId(Long datasetId, Long organizationId);

	List<Feedback> findAllByDatasetIdAndOrganizationIdOrderById(
			Long datasetId,
			Long organizationId
	);
}
