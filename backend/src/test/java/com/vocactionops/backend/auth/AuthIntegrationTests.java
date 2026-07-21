package com.vocactionops.backend.auth;

import com.vocactionops.backend.auth.config.JwtProperties;
import com.vocactionops.backend.auth.domain.RefreshToken;
import com.vocactionops.backend.auth.repository.RefreshTokenRepository;
import com.vocactionops.backend.auth.token.JwtTokenProvider;
import com.vocactionops.backend.auth.token.RefreshTokenCodec;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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
	private DatabaseCleaner databaseCleaner;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private JwtProperties jwtProperties;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private RefreshTokenCodec refreshTokenCodec;

	private Organization firstOrganization;
	private Organization secondOrganization;
	private User admin;
	private User viewer;
	private User otherOrganizationUser;

	@BeforeEach
	void setUp() {
		databaseCleaner.clean();

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
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(1209600))
				.andExpect(jsonPath("$.data.password").doesNotExist())
				.andExpect(jsonPath("$.data.passwordHash").doesNotExist())
				.andReturn();

		String token = accessToken(result);
		String rawRefreshToken = refreshToken(result);
		Jwt jwt = jwtDecoder.decode(token);
		RefreshToken storedRefreshToken = refreshTokenRepository.findAll().get(0);

		assertThat(admin.getPasswordHash()).startsWith("$2");
		assertThat(jwt.getSubject()).isEqualTo(admin.getId().toString());
		assertThat(((Number) jwt.getClaim(JwtTokenProvider.ORGANIZATION_ID_CLAIM)).longValue())
				.isEqualTo(firstOrganization.getId());
		assertThat(jwt.getClaimAsString(JwtTokenProvider.EMAIL_CLAIM)).isEqualTo(admin.getEmail());
		assertThat(jwt.getClaimAsString(JwtTokenProvider.ROLE_CLAIM)).isEqualTo(Role.ADMIN.name());
		assertThat(storedRefreshToken.getTokenHash())
				.isEqualTo(refreshTokenCodec.hash(rawRefreshToken))
				.isNotEqualTo(rawRefreshToken);
		assertThat(storedRefreshToken.getExpiresAt())
				.isAfter(storedRefreshToken.getCreatedAt());
	}

	@Test
	void rotatesRefreshTokenAndMarksPreviousTokenAsUsed() throws Exception {
		MvcResult loginResult = loginResult(admin.getEmail(), ADMIN_PASSWORD);
		String previousAccessToken = accessToken(loginResult);
		String previousRefreshToken = refreshToken(loginResult);

		MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshRequest(previousRefreshToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.data.refreshTokenExpiresIn").value(1209600))
				.andReturn();
		String nextAccessToken = accessToken(refreshResult);
		String nextRefreshToken = refreshToken(refreshResult);

		assertThat(nextAccessToken).isNotEqualTo(previousAccessToken);
		assertThat(nextRefreshToken).isNotEqualTo(previousRefreshToken);
		List<RefreshToken> storedTokens = refreshTokenRepository.findAll();
		assertThat(storedTokens).hasSize(2);
		RefreshToken previous = findByRawToken(storedTokens, previousRefreshToken);
		RefreshToken next = findByRawToken(storedTokens, nextRefreshToken);
		assertThat(previous.getUsedAt()).isNotNull();
		assertThat(previous.getReplacedByToken()).isNotNull();
		assertThat(previous.getReplacedByToken().getId()).isEqualTo(next.getId());
		assertThat(previous.getFamilyId()).isEqualTo(next.getFamilyId());
		assertThat(next.getUsedAt()).isNull();
		assertThat(next.getRevokedAt()).isNull();
	}

	@Test
	void revokesTokenFamilyWhenUsedRefreshTokenIsReused() throws Exception {
		MvcResult loginResult = loginResult(admin.getEmail(), ADMIN_PASSWORD);
		String previousRefreshToken = refreshToken(loginResult);
		MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshRequest(previousRefreshToken)))
				.andExpect(status().isOk())
				.andReturn();
		String currentRefreshToken = refreshToken(refreshResult);

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshRequest(previousRefreshToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REFRESH_TOKEN.code()));

		assertThat(refreshTokenRepository.findAll())
				.hasSize(2)
				.allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshRequest(currentRefreshToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REFRESH_TOKEN.code()));
	}

	@Test
	void logoutRevokesRefreshTokenAndIsIdempotentForUnknownToken() throws Exception {
		MvcResult loginResult = loginResult(admin.getEmail(), ADMIN_PASSWORD);
		String rawRefreshToken = refreshToken(loginResult);

		mockMvc.perform(post("/api/v1/auth/logout")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshRequest(rawRefreshToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").value("로그아웃되었습니다."));
		assertThat(refreshTokenRepository.findAll().get(0).getRevokedAt()).isNotNull();

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshRequest(rawRefreshToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REFRESH_TOKEN.code()));
		mockMvc.perform(post("/api/v1/auth/logout")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshRequest("unknown-refresh-token")))
				.andExpect(status().isOk());
	}

	@Test
	void rejectsExpiredAndForgedRefreshTokens() throws Exception {
		String expiredRawToken = refreshTokenCodec.generate();
		Instant now = Instant.now();
		refreshTokenRepository.save(new RefreshToken(
				admin,
				refreshTokenCodec.hash(expiredRawToken),
				UUID.randomUUID().toString(),
				now.minus(2, ChronoUnit.DAYS),
				now.minus(1, ChronoUnit.DAYS)
		));

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshRequest(expiredRawToken)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REFRESH_TOKEN.code()));
		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(refreshRequest("forged-refresh-token")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_REFRESH_TOKEN.code()));
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
		return accessToken(loginResult(email, password));
	}

	private MvcResult loginResult(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(loginRequest(email, password)))
				.andExpect(status().isOk())
				.andReturn();
	}

	private String accessToken(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.at("/data/accessToken")
				.asString();
	}

	private String refreshToken(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString())
				.at("/data/refreshToken")
				.asString();
	}

	private RefreshToken findByRawToken(List<RefreshToken> tokens, String rawToken) {
		String tokenHash = refreshTokenCodec.hash(rawToken);
		return tokens.stream()
				.filter(token -> token.getTokenHash().equals(tokenHash))
				.findFirst()
				.orElseThrow();
	}

	private String refreshRequest(String refreshToken) {
		return """
				{
				  "refreshToken": "%s"
				}
				""".formatted(refreshToken);
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
