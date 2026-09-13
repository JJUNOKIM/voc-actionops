package com.vocactionops.backend.user;

import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
import com.vocactionops.backend.support.DatabaseCleaner;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserManagementIntegrationTests {

	private static final String PASSWORD = "Password123!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private DatabaseCleaner databaseCleaner;

	private User admin;
	private User viewer;
	private User otherOrganizationUser;

	@BeforeEach
	void setUp() {
		databaseCleaner.clean();

		Organization organization = organizationRepository.save(new Organization("VOC Team"));
		Organization otherOrganization = organizationRepository.save(new Organization("Other Team"));

		admin = saveUser(organization, "admin@example.com", Role.ADMIN);
		viewer = saveUser(organization, "viewer@example.com", Role.VIEWER);
		otherOrganizationUser = saveUser(otherOrganization, "other@example.com", Role.VIEWER);
	}

	@Test
	void adminChangesOrganizationUserRole() throws Exception {
		String accessToken = login(admin.getEmail());

		mockMvc.perform(patch("/api/v1/users/{userId}/role", viewer.getId())
					.header("Authorization", bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content(roleRequest(Role.DEVELOPER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(viewer.getId()))
				.andExpect(jsonPath("$.data.role").value(Role.DEVELOPER.name()));

		assertThat(userRepository.findById(viewer.getId()).orElseThrow().getRole())
				.isEqualTo(Role.DEVELOPER);
	}

	@Test
	void adminCannotChangeOwnRole() throws Exception {
		String accessToken = login(admin.getEmail());

		mockMvc.perform(patch("/api/v1/users/{userId}/role", admin.getId())
					.header("Authorization", bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content(roleRequest(Role.VIEWER)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()));

		assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole())
				.isEqualTo(Role.ADMIN);
	}

	@Test
	void adminCannotChangeUserFromAnotherOrganization() throws Exception {
		String accessToken = login(admin.getEmail());

		mockMvc.perform(patch("/api/v1/users/{userId}/role", otherOrganizationUser.getId())
					.header("Authorization", bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content(roleRequest(Role.DEVELOPER)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_FOUND.code()));
	}

	@Test
	void nonAdminCannotChangeUserRole() throws Exception {
		String accessToken = login(viewer.getEmail());

		mockMvc.perform(patch("/api/v1/users/{userId}/role", admin.getId())
					.header("Authorization", bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content(roleRequest(Role.PM)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.code()));
	}

	private User saveUser(Organization organization, String email, Role role) {
		return userRepository.save(new User(
				organization,
				email,
				passwordEncoder.encode(PASSWORD),
				email.substring(0, email.indexOf('@')),
				role
		));
	}

	private String login(String email) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "email": "%s",
							  "password": "%s"
							}
							""".formatted(email, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn();

		return objectMapper.readTree(result.getResponse().getContentAsString())
				.at("/data/accessToken")
				.asString();
	}

	private String roleRequest(Role role) {
		return """
				{
				  "role": "%s"
				}
				""".formatted(role.name());
	}

	private String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}
}
