package com.vocactionops.backend.dataset.repository;

import com.vocactionops.backend.dataset.domain.DatasetValidationError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DatasetValidationErrorRepository extends JpaRepository<DatasetValidationError, Long> {

	@Query("""
			SELECT error
			FROM DatasetValidationError error
			WHERE error.dataset.id = :datasetId
			  AND error.dataset.organization.id = :organizationId
			""")
	Page<DatasetValidationError> findPageByDatasetAndOrganization(
			@Param("datasetId") Long datasetId,
			@Param("organizationId") Long organizationId,
			Pageable pageable
	);
}
