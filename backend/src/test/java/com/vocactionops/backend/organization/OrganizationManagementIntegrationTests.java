package com.vocactionops.backend.organization;

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
class OrganizationManagementIntegrationTests {

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

	private Organization organization;
	private User admin;
	private User viewer;

	@BeforeEach
	void setUp() {
		databaseCleaner.clean();
		organization = organizationRepository.save(new Organization("VOC Team"));
		admin = saveUser("admin@example.com", Role.ADMIN);
		viewer = saveUser("viewer@example.com", Role.VIEWER);
	}

	@Test
	void adminChangesOwnOrganizationName() throws Exception {
		mockMvc.perform(patch("/api/v1/organizations/me")
					.header("Authorization", bearer(login(admin.getEmail())))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "name": "  Customer Lab  "
							}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(organization.getId()))
				.andExpect(jsonPath("$.data.name").value("Customer Lab"));

		assertThat(organizationRepository.findById(organization.getId()).orElseThrow().getName())
				.isEqualTo("Customer Lab");
	}

	@Test
	void nonAdminCannotChangeOrganizationName() throws Exception {
		mockMvc.perform(patch("/api/v1/organizations/me")
					.header("Authorization", bearer(login(viewer.getEmail())))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "name": "Changed Team"
							}
							"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.code()));
	}

	@Test
	void rejectsBlankOrganizationName() throws Exception {
		mockMvc.perform(patch("/api/v1/organizations/me")
					.header("Authorization", bearer(login(admin.getEmail())))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "name": "   "
							}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REQUEST.code()));
	}

	private User saveUser(String email, Role role) {
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
				.get("data")
				.get("accessToken")
				.asText();
	}

	private String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}
}
