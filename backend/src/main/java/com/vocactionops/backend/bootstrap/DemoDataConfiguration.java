package com.vocactionops.backend.bootstrap;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DemoDataProperties.class)
public class DemoDataConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
	ApplicationRunner demoDataInitializer(DemoDataBootstrapService bootstrapService) {
		return arguments -> bootstrapService.initialize();
	}
}
