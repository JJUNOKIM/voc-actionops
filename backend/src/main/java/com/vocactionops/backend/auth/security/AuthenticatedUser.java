package com.vocactionops.backend.auth.security;

import com.vocactionops.backend.user.domain.Role;

public record AuthenticatedUser(
		Long userId,
		Long organizationId,
		String email,
		Role role
) {
}
