package com.vocactionops.backend.analysis.repository;

import com.vocactionops.backend.analysis.domain.FeedbackAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedbackAnalysisRepository extends JpaRepository<FeedbackAnalysis, Long> {

	Optional<FeedbackAnalysis> findByFeedbackIdAndFeedbackOrganizationId(
			Long feedbackId,
			Long organizationId
	);
}
