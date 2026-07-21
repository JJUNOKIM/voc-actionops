import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.providers.base import AnalysisProviderError
from app.schemas import FeedbackAnalysisRequest, ProviderAnalysis, Sentiment


VALID_PAYLOAD = {
    "feedback_id": 10,
    "content": "쿠폰 적용 후 결제가 안 돼요.",
    "rating": 1,
    "language": "ko",
    "product_name": "모바일 앱",
    "customer_segment": "신규 고객",
}


def test_health_check_is_public(client: TestClient) -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP", "provider": "deterministic"}


@pytest.mark.parametrize("api_key", [None, "wrong-key"])
def test_analysis_rejects_invalid_internal_api_key(
    client: TestClient,
    api_key: str | None,
) -> None:
    headers = {} if api_key is None else {"X-Internal-API-Key": api_key}

    response = client.post(
        "/internal/v1/feedback-analysis",
        json=VALID_PAYLOAD,
        headers=headers,
    )

    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED"


def test_analysis_returns_backend_compatible_result(client: TestClient) -> None:
    response = client.post(
        "/internal/v1/feedback-analysis",
        json=VALID_PAYLOAD,
        headers={"X-Internal-API-Key": "test-internal-key"},
    )

    assert response.status_code == 200
    assert response.json() == {
        "sentiment": "NEGATIVE",
        "sentiment_score": -1.0,
        "category": "PAYMENT",
        "urgency_score": 0.75,
        "summary": "쿠폰 적용 후 결제가 안 돼요.",
        "confidence_score": 0.85,
        "feedback_id": 10,
        "model_name": "deterministic-v1",
    }


def test_analysis_rejects_unknown_request_fields(client: TestClient) -> None:
    response = client.post(
        "/internal/v1/feedback-analysis",
        json={**VALID_PAYLOAD, "unknown": "value"},
        headers={"X-Internal-API-Key": "test-internal-key"},
    )

    assert response.status_code == 422


def test_provider_failure_returns_bad_gateway(settings: Settings) -> None:
    class FailingProvider:
        model_name = "failing-provider"

        def analyze(self, request: FeedbackAnalysisRequest) -> ProviderAnalysis:
            raise AnalysisProviderError("analysis provider is unavailable")

    client = TestClient(create_app(settings=settings, provider=FailingProvider()))

    response = client.post(
        "/internal/v1/feedback-analysis",
        json=VALID_PAYLOAD,
        headers={"X-Internal-API-Key": "test-internal-key"},
    )

    assert response.status_code == 502
    assert response.json() == {
        "code": "ANALYSIS_PROVIDER_ERROR",
        "message": "analysis provider is unavailable",
    }


def test_custom_provider_result_is_validated(settings: Settings) -> None:
    class StubProvider:
        model_name = "stub-v1"

        def analyze(self, request: FeedbackAnalysisRequest) -> ProviderAnalysis:
            return ProviderAnalysis(
                sentiment=Sentiment.NEUTRAL,
                sentiment_score=0,
                category="OTHER",
                urgency_score=0.2,
                summary="요약",
                confidence_score=0.8,
            )

    client = TestClient(create_app(settings=settings, provider=StubProvider()))

    response = client.post(
        "/internal/v1/feedback-analysis",
        json=VALID_PAYLOAD,
        headers={"X-Internal-API-Key": "test-internal-key"},
    )

    assert response.status_code == 200
    assert response.json()["model_name"] == "stub-v1"
