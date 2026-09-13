package com.vocactionops.backend.organization.application;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

	private final OrganizationRepository organizationRepository;

	public OrganizationService(OrganizationRepository organizationRepository) {
		this.organizationRepository = organizationRepository;
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public OrganizationView changeName(AuthenticatedUser authenticatedUser, String name) {
		Organization organization = organizationRepository.findById(authenticatedUser.organizationId())
				.orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

		organization.changeName(name);
		return new OrganizationView(organization.getId(), organization.getName());
	}

	public record OrganizationView(Long id, String name) {
	}
}
