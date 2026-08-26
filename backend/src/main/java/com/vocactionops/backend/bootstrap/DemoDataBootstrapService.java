package com.vocactionops.backend.bootstrap;

import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoDataBootstrapService {

	private final DemoDataProperties properties;
	private final OrganizationRepository organizationRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public DemoDataBootstrapService(
			DemoDataProperties properties,
			OrganizationRepository organizationRepository,
			UserRepository userRepository,
			PasswordEncoder passwordEncoder
	) {
		this.properties = properties;
		this.organizationRepository = organizationRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public void initialize() {
		String email = properties.userEmail().trim();
		if (userRepository.existsByEmailIgnoreCase(email)) {
			return;
		}

		Organization organization = organizationRepository.save(
				new Organization(properties.organizationName().trim())
		);
		userRepository.save(new User(
				organization,
				email,
				passwordEncoder.encode(properties.userPassword()),
				properties.userName().trim(),
				Role.ADMIN
		));
	}
}
