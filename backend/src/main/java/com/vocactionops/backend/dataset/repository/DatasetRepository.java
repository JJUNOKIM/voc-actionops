package com.vocactionops.backend.dataset.repository;

import com.vocactionops.backend.dataset.domain.Dataset;
import com.vocactionops.backend.dataset.domain.DatasetStatus;
import com.vocactionops.backend.dataset.domain.SourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {

	@Query("""
			SELECT dataset
			FROM Dataset dataset
			WHERE dataset.organization.id = :organizationId
			  AND (:sourceType IS NULL OR dataset.sourceType = :sourceType)
			  AND (:status IS NULL OR dataset.status = :status)
			""")
	Page<Dataset> findPageByOrganization(
			@Param("organizationId") Long organizationId,
			@Param("sourceType") SourceType sourceType,
			@Param("status") DatasetStatus status,
			Pageable pageable
	);

	Optional<Dataset> findByIdAndOrganizationId(Long id, Long organizationId);
}
