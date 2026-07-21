package com.vocactionops.backend.analysis.job.repository;

import com.vocactionops.backend.analysis.job.domain.AnalysisJobItem;
import com.vocactionops.backend.analysis.job.domain.AnalysisJobItemStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface AnalysisJobItemRepository extends JpaRepository<AnalysisJobItem, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AnalysisJobItem> findFirstByJobIdAndStatusOrderById(
			String jobId,
			AnalysisJobItemStatus status
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AnalysisJobItem> findByIdAndJobId(Long id, String jobId);

	List<AnalysisJobItem> findByJobIdAndStatus(
			String jobId,
			AnalysisJobItemStatus status
	);

	long countByJobId(String jobId);

	List<AnalysisJobItem> findAllByJobIdOrderById(String jobId);
}
