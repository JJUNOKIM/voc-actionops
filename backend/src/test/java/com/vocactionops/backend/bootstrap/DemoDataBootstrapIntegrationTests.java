package com.vocactionops.backend.bootstrap;

import com.vocactionops.backend.organization.repository.OrganizationRepository;
import com.vocactionops.backend.support.DatabaseCleaner;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DemoDataBootstrapIntegrationTests {

	@Autowired
	private DemoDataBootstrapService bootstrapService;

	@Autowired
	private DemoDataProperties properties;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private DatabaseCleaner databaseCleaner;

	@BeforeEach
	void setUp() {
		databaseCleaner.clean();
	}

	@Test
	void createsDemoOrganizationAndAdminOnlyOnce() {
		bootstrapService.initialize();
		bootstrapService.initialize();

		assertThat(organizationRepository.count()).isOne();
		assertThat(userRepository.count()).isOne();

		User user = userRepository.findByEmailIgnoreCase(properties.userEmail()).orElseThrow();
		assertThat(organizationRepository.findAll())
				.singleElement()
				.extracting(organization -> organization.getName())
				.isEqualTo(properties.organizationName());
		assertThat(user.getName()).isEqualTo(properties.userName());
		assertThat(user.getRole()).isEqualTo(Role.ADMIN);
		assertThat(passwordEncoder.matches(properties.userPassword(), user.getPasswordHash())).isTrue();
		assertThat(user.getPasswordHash()).isNotEqualTo(properties.userPassword());
	}
}
