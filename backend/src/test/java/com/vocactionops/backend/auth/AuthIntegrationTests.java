package com.vocactionops.backend.auth;

import com.vocactionops.backend.analysis.repository.AiCorrectionRepository;
import com.vocactionops.backend.analysis.repository.FeedbackAnalysisRepository;
import com.vocactionops.backend.auth.config.JwtProperties;
import com.vocactionops.backend.auth.token.JwtTokenProvider;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.dataset.repository.DatasetRepository;
import com.vocactionops.backend.dataset.repository.DatasetValidationErrorRepository;
import com.vocactionops.backend.feedback.repository.FeedbackRepository;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTests {

	private static final String ADMIN_PASSWORD = "Admin123!";
	private static final String VIEWER_PASSWORD = "Viewer123!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AiCorrectionRepository correctionRepository;

	@Autowired
	private FeedbackAnalysisRepository analysisRepository;

	@Autowired
	private DatasetValidationErrorRepository validationErrorRepository;

	@Autowired
	private FeedbackRepository feedbackRepository;

	@Autowired
	private DatasetRepository datasetRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JwtProperties jwtProperties;

	private Organization firstOrganization;
	private Organization secondOrganization;
	private User admin;
	private User viewer;
	private User otherOrganizationUser;

	@BeforeEach
	void setUp() {
		correctionRepository.deleteAll();
		analysisRepository.deleteAll();
		validationErrorRepository.deleteAll();
		feedbackRepository.deleteAll();
		datasetRepository.deleteAll();
		userRepository.deleteAll();
		organizationRepository.deleteAll();

		firstOrganization = organizationRepository.save(new Organization("VOC Team"));
		secondOrganization = organizationRepository.save(new Organization("Other Team"));

		admin = userRepository.save(new User(
				firstOrganization,
				"admin@example.com",
				passwordEncoder.encode(ADMIN_PASSWORD),
				"Admin User",
				Role.ADMIN
		));
		viewer = userRepository.save(new User(
				firstOrganization,
				"viewer@example.com",
				passwordEncoder.encode(VIEWER_PASSWORD),
				"Viewer User",
				Role.VIEWER
		));
		otherOrganizationUser = userRepository.save(new User(
				secondOrganization,
				"other@example.com",
				passwordEncoder.encode(ADMIN_PASSWORD),
				"Other User",
				Role.ADMIN
		));
	}

	@Test
	void loginReturnsAccessTokenWithIdentityClaims() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginRequest("  ADMIN@EXAMPLE.COM  ", ADMIN_PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.data.expiresIn").value(1800))
				.andExpect(jsonPath("$.data.password").doesNotExist())
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist())
				.andReturn();

		String token = accessToken(result);
		Jwt jwt = jwtDecoder.decode(token);

		assertThat(admin.getPasswordHash()).startsWith("$2");
		assertThat(jwt.getSubject()).isEqualTo(admin.getId().toString());
		assertThat(((Number) jwt.getClaim(JwtTokenProvider.ORGANIZATION_ID_CLAIM)).longValue())
				.isEqualTo(firstOrganization.getId());
		assertThat(jwt.getClaimAsString(JwtTokenProvider.EMAIL_CLAIM)).isEqualTo(admin.getEmail());
		assertThat(jwt.getClaimAsString(JwtTokenProvider.ROLE_CLAIM)).isEqualTo(Role.ADMIN.name());
	}

	@Test
	void rejectsInvalidCredentialsWithoutRevealingWhichValueFailed() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginRequest(admin.getEmail(), "wrong-password")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_CREDENTIALS.code()));

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginRequest("unknown@example.com", ADMIN_PASSWORD)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_CREDENTIALS.code()));
	}

	@Test
	void rejectsUnauthenticatedAndInvalidTokenRequestsWithUnauthorizedResponse() throws Exception {
		mockMvc.perform(get("/api/v1/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.code()));

		mockMvc.perform(get("/api/v1/users/me")
						.header("Authorization", "Bearer invalid-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.code()));
	}

	@Test
	void rejectsSignedTokensWithMissingOrInvalidClaims() throws Exception {
		Instant issuedAt = Instant.now();
		JwtClaimsSet missingRoleClaims = JwtClaimsSet.builder()
				.issuer(jwtProperties.issuer())
				.subject(admin.getId().toString())
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plusSeconds(300))
				.claim(JwtTokenProvider.ORGANIZATION_ID_CLAIM, firstOrganization.getId())
				.claim(JwtTokenProvider.EMAIL_CLAIM, admin.getEmail())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
		String missingRoleToken = jwtEncoder
				.encode(JwtEncoderParameters.from(header, missingRoleClaims))
				.getTokenValue();

		mockMvc.perform(get("/api/v1/users/me")
						.header("Authorization", bearer(missingRoleToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.code()));

		JwtClaimsSet invalidOrganizationClaims = JwtClaimsSet.builder()
				.issuer(jwtProperties.issuer())
				.subject(admin.getId().toString())
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plusSeconds(300))
				.claim(JwtTokenProvider.ORGANIZATION_ID_CLAIM, "invalid")
				.claim(JwtTokenProvider.EMAIL_CLAIM, admin.getEmail())
				.claim(JwtTokenProvider.ROLE_CLAIM, Role.ADMIN.name())
				.build();
		String invalidOrganizationToken = jwtEncoder
				.encode(JwtEncoderParameters.from(header, invalidOrganizationClaims))
				.getTokenValue();

		mockMvc.perform(get("/api/v1/users/me")
						.header("Authorization", bearer(invalidOrganizationToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.code()));
	}

	@Test
	void returnsCurrentUserFromAuthenticatedPrincipal() throws Exception {
		String token = login(admin.getEmail(), ADMIN_PASSWORD);

		mockMvc.perform(get("/api/v1/users/me")
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(admin.getId()))
				.andExpect(jsonPath("$.data.organizationId").value(firstOrganization.getId()))
				.andExpect(jsonPath("$.data.organizationName").value(firstOrganization.getName()))
				.andExpect(jsonPath("$.data.email").value(admin.getEmail()))
				.andExpect(jsonPath("$.data.role").value(Role.ADMIN.name()))
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist());
	}

	@Test
	void adminListsOnlyUsersFromAuthenticatedOrganization() throws Exception {
		String token = login(admin.getEmail(), ADMIN_PASSWORD);

		mockMvc.perform(get("/api/v1/users")
						.queryParam("organizationId", secondOrganization.getId().toString())
						.header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2))
				.andExpect(jsonPath("$.data[*].email", containsInAnyOrder(
						admin.getEmail(),
						viewer.getEmail()
				)))
				.andExpect(jsonPath("$.data[*].email", not(hasItem(otherOrganizationUser.getEmail()))))
				.andExpect(jsonPath("$.data[*].passwordHash").doesNotExist());
	}

	@Test
	void viewerCannotListOrganizationUsers() throws Exception {
		String token = login(viewer.getEmail(), VIEWER_PASSWORD);

		mockMvc.perform(get("/api/v1/users")
						.header("Authorization", bearer(token)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.code()));
	}

	private String login(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginRequest(email, password)))
				.andExpect(status().isOk())
				.andReturn();

		return accessToken(result);
	}

	private String accessToken(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.at("/data/accessToken")
				.asString();
	}

	private String loginRequest(String email, String password) {
		return """
				{
				  "email": "%s",
				  "password": "%s"
				}
				""".formatted(email, password);
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}
}
