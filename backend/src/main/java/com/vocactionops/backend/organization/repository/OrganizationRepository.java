package com.vocactionops.backend.organization.repository;

import com.vocactionops.backend.organization.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
}
