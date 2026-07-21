package com.vocactionops.backend.analysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("app.ai-worker")
public record AiWorkerProperties(
		URI baseUrl,
		String apiKey,
		String modelName,
		Duration connectTimeout,
		Duration readTimeout,
		int maxAttempts
) {

	public AiWorkerProperties {
		if (baseUrl == null) {
			throw new IllegalArgumentException("app.ai-worker.base-url must not be null");
		}
		if (apiKey == null || apiKey.isBlank() || apiKey.trim().length() < 16) {
			throw new IllegalArgumentException("app.ai-worker.api-key is invalid");
		}
		if (modelName == null || modelName.isBlank() || modelName.trim().length() > 100) {
			throw new IllegalArgumentException("app.ai-worker.model-name is invalid");
		}
		if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
			throw new IllegalArgumentException("app.ai-worker.connect-timeout must be positive");
		}
		if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
			throw new IllegalArgumentException("app.ai-worker.read-timeout must be positive");
		}
		if (maxAttempts < 1 || maxAttempts > 10) {
			throw new IllegalArgumentException("app.ai-worker.max-attempts must be between 1 and 10");
		}
		apiKey = apiKey.trim();
		modelName = modelName.trim();
	}
}
