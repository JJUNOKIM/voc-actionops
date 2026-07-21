package com.vocactionops.backend.analysis.job.repository;

import com.vocactionops.backend.analysis.job.domain.AnalysisJob;
import com.vocactionops.backend.analysis.job.domain.AnalysisJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			SELECT job
			FROM AnalysisJob job
			JOIN FETCH job.dataset
			WHERE job.id = :jobId
			""")
	Optional<AnalysisJob> findByIdForUpdate(@Param("jobId") String jobId);

	Optional<AnalysisJob> findTopByDatasetIdAndOrganizationIdOrderByCreatedAtDesc(
			Long datasetId,
			Long organizationId
	);

	@Query("""
			SELECT job.id
			FROM AnalysisJob job
			WHERE job.status IN :statuses
			ORDER BY job.createdAt
			""")
	List<String> findIdsByStatusIn(
			@Param("statuses") Collection<AnalysisJobStatus> statuses
	);
}
