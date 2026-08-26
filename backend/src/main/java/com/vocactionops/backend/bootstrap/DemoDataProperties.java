package com.vocactionops.backend.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.demo")
public record DemoDataProperties(
		boolean enabled,
		String organizationName,
		String userEmail,
		String userPassword,
		String userName
) {

	public DemoDataProperties {
		if (isBlank(organizationName) || organizationName.trim().length() > 100) {
			throw new IllegalArgumentException("app.demo.organization-name must be 1 to 100 characters");
		}
		if (isBlank(userEmail) || userEmail.trim().length() > 255) {
			throw new IllegalArgumentException("app.demo.user-email must be 1 to 255 characters");
		}
		if (userPassword == null || userPassword.length() < 8) {
			throw new IllegalArgumentException("app.demo.user-password must be at least 8 characters");
		}
		if (isBlank(userName) || userName.trim().length() > 100) {
			throw new IllegalArgumentException("app.demo.user-name must be 1 to 100 characters");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
