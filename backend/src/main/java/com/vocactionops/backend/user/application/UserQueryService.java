package com.vocactionops.backend.user.application;

import com.vocactionops.backend.auth.security.AuthenticatedUser;
import com.vocactionops.backend.common.exception.CustomException;
import com.vocactionops.backend.common.exception.ErrorCode;
import com.vocactionops.backend.user.domain.Role;
import com.vocactionops.backend.user.domain.User;
import com.vocactionops.backend.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserQueryService {

	private final UserRepository userRepository;

	public UserQueryService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public UserProfile getCurrentUser(AuthenticatedUser authenticatedUser) {
		User user = userRepository.findByIdAndOrganizationId(
						authenticatedUser.userId(),
						authenticatedUser.organizationId()
				)
				.orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));

		return new UserProfile(
				user.getId(),
				user.getOrganization().getId(),
				user.getOrganization().getName(),
				user.getEmail(),
				user.getName(),
				user.getRole()
		);
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'PM')")
	public List<OrganizationUser> getOrganizationUsers(AuthenticatedUser authenticatedUser) {
		return userRepository.findAllByOrganizationId(authenticatedUser.organizationId())
				.stream()
				.sorted(Comparator.comparing(User::getId))
				.map(user -> new OrganizationUser(
						user.getId(),
						user.getEmail(),
						user.getName(),
						user.getRole()
				))
				.toList();
	}

	public record UserProfile(
			Long id,
			Long organizationId,
			String organizationName,
			String email,
			String name,
			Role role
	) {
	}

	public record OrganizationUser(
			Long id,
			String email,
			String name,
			Role role
	) {
	}
}
