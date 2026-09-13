package com.vocactionops.backend.bootstrap;

import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DemoDataBootstrapService {

	private static final List<DemoUser> DEMO_USERS = List.of(
			new DemoUser("pm@voc-actionops.local", "Demo PM", Role.PM),
			new DemoUser("cs@voc-actionops.local", "Demo CS", Role.CS),
			new DemoUser("developer@voc-actionops.local", "Demo Developer", Role.DEVELOPER),
			new DemoUser("viewer@voc-actionops.local", "Demo Viewer", Role.VIEWER)
	);

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
		ensureOrganizationUsers(admin);
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

	private void ensureOrganizationUsers(User admin) {
		DEMO_USERS.forEach(demoUser -> userRepository.findByEmailIgnoreCase(demoUser.email())
				.orElseGet(() -> userRepository.save(new User(
						admin.getOrganization(),
						demoUser.email(),
						passwordEncoder.encode(properties.userPassword()),
						demoUser.name(),
						demoUser.role()
				))));
	}

	private record DemoUser(String email, String name, Role role) {
	}
}
