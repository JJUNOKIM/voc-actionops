package com.vocactionops.backend.analysis.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpAiWorkerClient implements AiWorkerClient {

	private final RestClient restClient;

	public HttpAiWorkerClient(
			@Qualifier("aiWorkerRestClient") RestClient restClient
	) {
		this.restClient = restClient;
	}

	@Override
	public AnalysisResult analyze(AnalysisRequest request) {
		try {
			AnalysisResult result = restClient.post()
					.uri("/internal/v1/feedback-analysis")
					.body(request)
					.retrieve()
					.body(AnalysisResult.class);
			if (result == null || !request.feedbackId().equals(result.feedbackId())) {
				throw new AiWorkerException("AI Worker returned an invalid response");
			}
			return result;
		} catch (AiWorkerException exception) {
			throw exception;
		} catch (RestClientException exception) {
			throw new AiWorkerException("AI Worker request failed", exception);
		}
	}
}
