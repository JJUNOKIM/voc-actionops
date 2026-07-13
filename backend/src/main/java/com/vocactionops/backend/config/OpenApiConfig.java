package com.vocactionops.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
		info = @Info(
				title = "VOC ActionOps API",
				version = "v1",
				description = "Customer feedback analysis and action management API"
		)
)
public class OpenApiConfig {
}
