package com.vocactionops.backend.organization.repository;

import com.vocactionops.backend.organization.domain.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

	@Query("SELECT organization.id FROM Organization organization ORDER BY organization.id")
	List<Long> findAllIds();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT organization FROM Organization organization WHERE organization.id = :id")
	Optional<Organization> findByIdForUpdate(@Param("id") Long id);
}
