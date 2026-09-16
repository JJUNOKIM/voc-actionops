package com.vocactionops.backend.user;

import com.vocactionops.backend.auth.repository.RefreshTokenRepository;
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
	private RefreshTokenRepository refreshTokenRepository;

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
	void adminCreatesUserInOwnOrganization() throws Exception {
		String accessToken = login(admin.getEmail());

		mockMvc.perform(post("/api/v1/users")
					.header("Authorization", bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content(createUserRequest(
							"  NEW.USER@EXAMPLE.COM  ",
							"New User",
							Role.CS,
							"NewPassword123!"
					)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("new.user@example.com"))
				.andExpect(jsonPath("$.data.name").value("New User"))
				.andExpect(jsonPath("$.data.role").value(Role.CS.name()))
				.andExpect(jsonPath("$.data.password").doesNotExist())
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist());

		User createdUser = userRepository.findByEmailIgnoreCase("new.user@example.com").orElseThrow();
		assertThat(createdUser.getOrganization().getId())
				.isEqualTo(admin.getOrganization().getId());
		assertThat(passwordEncoder.matches("NewPassword123!", createdUser.getPasswordHash())).isTrue();
		assertThat(createdUser.getPasswordHash()).isNotEqualTo("NewPassword123!");
	}

	@Test
	void rejectsDuplicatedUserEmailIgnoringCase() throws Exception {
		String accessToken = login(admin.getEmail());

		mockMvc.perform(post("/api/v1/users")
					.header("Authorization", bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content(createUserRequest(
							" VIEWER@EXAMPLE.COM ",
							"Another Viewer",
							Role.VIEWER,
							"NewPassword123!"
					)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code")
						.value(ErrorCode.DUPLICATED_RESOURCE.code()));
	}

	@Test
	void rejectsInvalidUserInput() throws Exception {
		String accessToken = login(admin.getEmail());

		mockMvc.perform(post("/api/v1/users")
					.header("Authorization", bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content(createUserRequest(
							"invalid-email",
							" ",
							Role.PM,
							"short"
					)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()))
				.andExpect(jsonPath("$.error.details.length()").value(3));
	}

	@Test
	void nonAdminCannotCreateUser() throws Exception {
		String accessToken = login(viewer.getEmail());

		mockMvc.perform(post("/api/v1/users")
					.header("Authorization", bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content(createUserRequest(
							"new.user@example.com",
							"New User",
							Role.CS,
							"NewPassword123!"
					)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.code()));
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

	@Test
	void userChangesOwnPassword() throws Exception {
		String accessToken = login(viewer.getEmail());

		mockMvc.perform(patch("/api/v1/users/me/password")
					.header("Authorization", bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content(passwordRequest(PASSWORD, "NewPassword123!")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다."));

		User changedUser = userRepository.findById(viewer.getId()).orElseThrow();
		assertThat(passwordEncoder.matches("NewPassword123!", changedUser.getPasswordHash())).isTrue();
		assertThat(refreshTokenRepository.findAll()).allMatch(token -> token.getRevokedAt() != null);
	}

	@Test
	void rejectsIncorrectCurrentPassword() throws Exception {
		String accessToken = login(viewer.getEmail());

		mockMvc.perform(patch("/api/v1/users/me/password")
					.header("Authorization", bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content(passwordRequest("WrongPassword!", "NewPassword123!")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code")
						.value(ErrorCode.INVALID_CURRENT_PASSWORD.code()));

		assertThat(passwordEncoder.matches(PASSWORD, userRepository.findById(viewer.getId())
				.orElseThrow()
				.getPasswordHash())).isTrue();
	}

	@Test
	void rejectsShortNewPassword() throws Exception {
		String accessToken = login(viewer.getEmail());

		mockMvc.perform(patch("/api/v1/users/me/password")
					.header("Authorization", bearer(accessToken))
					.contentType(MediaType.APPLICATION_JSON)
					.content(passwordRequest(PASSWORD, "short")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()));
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

	private String createUserRequest(
			String email,
			String name,
			Role role,
			String password
	) {
		return """
				{
				  "email": "%s",
				  "name": "%s",
				  "role": "%s",
				  "password": "%s"
				}
				""".formatted(email, name, role.name(), password);
	}

	private String passwordRequest(String currentPassword, String newPassword) {
		return """
				{
				  "currentPassword": "%s",
				  "newPassword": "%s"
				}
				""".formatted(currentPassword, newPassword);
	}

	private String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}
}
