package com.vocactionops.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
		info = @Info(
				title = "VOC ActionOps API",
				version = "v1",
				description = "Customer feedback analysis and action management API"
		)
)
@SecurityScheme(
		name = OpenApiConfig.BEARER_AUTH_SCHEME,
		type = SecuritySchemeType.HTTP,
		scheme = "bearer",
		bearerFormat = "JWT"
)
public class OpenApiConfig {

	public static final String BEARER_AUTH_SCHEME = "bearerAuth";
}
