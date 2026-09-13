package com.vocactionops.backend.user.application;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.user.application.UserQueryService.OrganizationUser;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserCommandService {

	private final UserRepository userRepository;

	public UserCommandService(UserRepository userRepository) {
		this.userRepository = userRepository;
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
		return new OrganizationUser(user.getId(), user.getEmail(), user.getName(), user.getRole());
	}
}
