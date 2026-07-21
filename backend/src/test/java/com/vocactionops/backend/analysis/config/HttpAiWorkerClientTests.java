package com.vocactionops.backend.analysis.config;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.vocactionops.backend.analysis.client.AiWorkerClient.AnalysisRequest;
import com.vocactionops.backend.analysis.client.AiWorkerClient.AnalysisResult;
import com.vocactionops.backend.analysis.client.AiWorkerException;
import com.vocactionops.backend.analysis.client.HttpAiWorkerClient;
import com.vocactionops.backend.analysis.domain.Sentiment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpAiWorkerClientTests {

	private static final String API_KEY = "test-internal-key-1234";
	private static final JsonMapper OBJECT_MAPPER = JsonMapper.shared();
	private static final AtomicReference<HttpHandler> HANDLER = new AtomicReference<>();
	private static HttpServer server;

	@BeforeAll
	static void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/internal/v1/feedback-analysis", exchange ->
				HANDLER.get().handle(exchange));
		server.start();
	}

	@AfterAll
	static void stopServer() {
		server.stop(0);
	}

	@Test
	void sendsInternalKeyAndSnakeCaseRequestAndReadsResponse() throws Exception {
		AtomicReference<String> receivedApiKey = new AtomicReference<>();
		AtomicReference<String> receivedProtocol = new AtomicReference<>();
		AtomicReference<JsonNode> receivedBody = new AtomicReference<>();
		HANDLER.set(exchange -> {
			receivedApiKey.set(exchange.getRequestHeaders().getFirst("X-Internal-API-Key"));
			receivedProtocol.set(exchange.getProtocol());
			receivedBody.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
			respond(exchange, 200, """
					{
					  "feedback_id": 42,
					  "sentiment": "POSITIVE",
					  "sentiment_score": 0.8123,
					  "category": "product_quality",
					  "urgency_score": 0.2,
					  "summary": "The customer is satisfied with the product.",
					  "confidence_score": 0.91,
					  "model_name": "deterministic-v1"
					}
					""");
		});
		HttpAiWorkerClient client = client(Duration.ofSeconds(1));

		AnalysisResult result = client.analyze(request());

		assertThat(receivedApiKey.get()).isEqualTo(API_KEY);
		assertThat(receivedProtocol.get()).isEqualTo("HTTP/1.1");
		assertThat(receivedBody.get().path("feedback_id").longValue()).isEqualTo(42L);
		assertThat(receivedBody.get().path("product_name").stringValue()).isEqualTo("Air Purifier");
		assertThat(receivedBody.get().path("customer_segment").stringValue()).isEqualTo("Enterprise");
		assertThat(receivedBody.get().size()).isEqualTo(6);
		assertThat(receivedBody.get().has("feedbackId")).isFalse();
		assertThat(receivedBody.get().has("productName")).isFalse();
		assertThat(result.feedbackId()).isEqualTo(42L);
		assertThat(result.sentiment()).isEqualTo(Sentiment.POSITIVE);
		assertThat(result.modelName()).isEqualTo("deterministic-v1");
	}

	@Test
	void convertsReadTimeoutToWorkerException() {
		HANDLER.set(exchange -> {
			try {
				Thread.sleep(300);
				respond(exchange, 200, "{}");
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
		});
		HttpAiWorkerClient client = client(Duration.ofMillis(50));

		assertThatThrownBy(() -> client.analyze(request()))
				.isInstanceOf(AiWorkerException.class)
				.hasMessage("AI Worker request failed");
	}

	private HttpAiWorkerClient client(Duration readTimeout) {
		AiWorkerProperties properties = new AiWorkerProperties(
				URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
				API_KEY,
				"deterministic-v1",
				Duration.ofSeconds(1),
				readTimeout,
				2
		);
		RestClient restClient = new AnalysisJobConfiguration().aiWorkerRestClient(properties);
		return new HttpAiWorkerClient(restClient);
	}

	private AnalysisRequest request() {
		return new AnalysisRequest(
				42L,
				"The product works well.",
				BigDecimal.valueOf(4.5),
				"en",
				"Air Purifier",
				"Enterprise"
		);
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] response = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, response.length);
		exchange.getResponseBody().write(response);
		exchange.close();
	}
}
