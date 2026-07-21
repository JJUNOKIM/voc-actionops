package com.vocactionops.backend.analysis.repository;

import com.vocactionops.backend.analysis.domain.AiCorrection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiCorrectionRepository extends JpaRepository<AiCorrection, Long> {

	@Query("""
			SELECT correction
			FROM AiCorrection correction
			WHERE correction.feedback.id = :feedbackId
			  AND correction.feedback.organization.id = :organizationId
			""")
	Page<AiCorrection> findPageByFeedbackAndOrganization(
			@Param("feedbackId") Long feedbackId,
			@Param("organizationId") Long organizationId,
			Pageable pageable
	);
}
