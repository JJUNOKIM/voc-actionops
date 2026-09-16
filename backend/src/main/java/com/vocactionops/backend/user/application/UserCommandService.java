package com.vocactionops.backend.user.application;

import com.vocactionops.backend.auth.application.RefreshTokenService;
import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.organization.domain.Organization;
import com.vocactionops.backend.organization.repository.OrganizationRepository;
import com.vocactionops.backend.user.application.UserQueryService.OrganizationUser;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserCommandService {

	private final UserRepository userRepository;
	private final OrganizationRepository organizationRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokenService;

	public UserCommandService(
			UserRepository userRepository,
			OrganizationRepository organizationRepository,
			PasswordEncoder passwordEncoder,
			RefreshTokenService refreshTokenService
	) {
		this.userRepository = userRepository;
		this.organizationRepository = organizationRepository;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenService = refreshTokenService;
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public OrganizationUser createUser(
			AuthenticatedUser authenticatedUser,
			String email,
			String password,
			String name,
			Role role
	) {
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new CustomException(ErrorCode.DUPLICATED_RESOURCE);
		}

		Organization organization = organizationRepository.findById(authenticatedUser.organizationId())
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
		User user = userRepository.save(new User(
				organization,
				email,
				passwordEncoder.encode(password),
				name.trim(),
				role
		));
		return organizationUser(user);
	}

	@Transactional
	@PreAuthorize("hasRole('ADMIN')")
	public OrganizationUser changeRole(
			AuthenticatedUser authenticatedUser,
			Long userId,
			Role role
	) {
		if (authenticatedUser.userId().equals(userId)) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		User user = userRepository.findByIdAndOrganizationId(
				userId,
				authenticatedUser.organizationId()
		).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

		user.changeRole(role);
		return organizationUser(user);
	}

	@Transactional
	public void changePassword(
			AuthenticatedUser authenticatedUser,
			String currentPassword,
			String newPassword
	) {
		User user = userRepository.findByIdAndOrganizationId(
				authenticatedUser.userId(),
				authenticatedUser.organizationId()
		).orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new CustomException(ErrorCode.INVALID_CURRENT_PASSWORD);
		}

		user.changePasswordHash(passwordEncoder.encode(newPassword));
		refreshTokenService.revokeAll(user.getId());
	}

	private OrganizationUser organizationUser(User user) {
		return new OrganizationUser(user.getId(), user.getEmail(), user.getName(), user.getRole());
	}
}
