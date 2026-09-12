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
	private final DemoScenarioSeedService scenarioSeedService;

	public DemoDataBootstrapService(
			DemoDataProperties properties,
			OrganizationRepository organizationRepository,
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			DemoScenarioSeedService scenarioSeedService
	) {
		this.properties = properties;
		this.organizationRepository = organizationRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.scenarioSeedService = scenarioSeedService;
	}

	@Transactional
	public void initialize() {
		String email = properties.userEmail().trim();
		User admin = userRepository.findByEmailIgnoreCase(email)
				.orElseGet(() -> createAdmin(email));
		scenarioSeedService.initialize(admin);
	}

	private User createAdmin(String email) {
		Organization organization = organizationRepository.save(
				new Organization(properties.organizationName().trim())
		);
		return userRepository.save(new User(
				organization,
				email,
				passwordEncoder.encode(properties.userPassword()),
				properties.userName().trim(),
				Role.ADMIN
		));
	}
}
