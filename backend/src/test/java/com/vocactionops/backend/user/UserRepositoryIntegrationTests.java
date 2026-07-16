package com.vocactionops.backend.user;

import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryIntegrationTests {

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void savesUserWithOrganizationAndRole() {
		Organization organization = organizationRepository.saveAndFlush(new Organization("VOC Team"));

		User user = userRepository.saveAndFlush(new User(
				organization,
				" PM@Example.com ",
				"encoded-password",
				"PM User",
				Role.PM
		));

		assertThat(user.getId()).isNotNull();
		assertThat(user.getOrganization().getId()).isEqualTo(organization.getId());
		assertThat(user.getEmail()).isEqualTo("pm@example.com");
		assertThat(user.getRole()).isEqualTo(Role.PM);
		assertThat(user.getCreatedAt()).isNotNull();
		assertThat(user.getUpdatedAt()).isNotNull();
		assertThat(userRepository.findByEmailIgnoreCase("PM@EXAMPLE.COM")).contains(user);
	}

	@Test
	void findsOnlyUsersInRequestedOrganization() {
		Organization firstOrganization = organizationRepository.save(new Organization("First Team"));
		Organization secondOrganization = organizationRepository.save(new Organization("Second Team"));

		userRepository.saveAll(List.of(
				new User(firstOrganization, "admin@example.com", "hash", "Admin", Role.ADMIN),
				new User(firstOrganization, "cs@example.com", "hash", "CS", Role.CS),
				new User(secondOrganization, "viewer@example.com", "hash", "Viewer", Role.VIEWER)
		));
		userRepository.flush();

		List<User> users = userRepository.findAllByOrganizationId(firstOrganization.getId());

		assertThat(users)
				.extracting(User::getEmail)
				.containsExactlyInAnyOrder("admin@example.com", "cs@example.com");
	}

	@Test
	void rejectsDuplicateEmailIgnoringCase() {
		Organization organization = organizationRepository.saveAndFlush(new Organization("VOC Team"));
		userRepository.saveAndFlush(new User(
				organization,
				"admin@example.com",
				"hash",
				"First Admin",
				Role.ADMIN
		));

		assertThatThrownBy(() -> userRepository.saveAndFlush(new User(
				organization,
				"ADMIN@EXAMPLE.COM",
				"another-hash",
				"Second Admin",
				Role.ADMIN
		)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
