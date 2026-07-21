from types import SimpleNamespace

import pytest

from app.providers.base import AnalysisProviderError
from app.providers.openai_provider import OpenAIAnalysisProvider
from app.schemas import FeedbackAnalysisRequest, ProviderAnalysis, Sentiment


class FakeResponses:
    def __init__(self, output: ProviderAnalysis | None) -> None:
        self.output = output
        self.request_arguments = None

    def parse(self, **kwargs: object) -> SimpleNamespace:
        self.request_arguments = kwargs
        return SimpleNamespace(output_parsed=self.output)


class FakeOpenAIClient:
    def __init__(self, output: ProviderAnalysis | None) -> None:
        self.responses = FakeResponses(output)


def test_openai_provider_uses_structured_response_schema() -> None:
    output = ProviderAnalysis(
        sentiment=Sentiment.NEGATIVE,
        sentiment_score=-0.8,
        category="PAYMENT",
        urgency_score=0.9,
        summary="결제 실패",
        confidence_score=0.88,
    )
    client = FakeOpenAIClient(output)
    provider = OpenAIAnalysisProvider(
        api_key="test-key",
        model_name="test-model",
        timeout_seconds=10,
        client=client,
    )

    result = provider.analyze(
        FeedbackAnalysisRequest(feedback_id=1, content="결제가 실패합니다.")
    )

    assert result == output
    assert client.responses.request_arguments["model"] == "test-model"
    assert client.responses.request_arguments["text_format"] is ProviderAnalysis
    user_content = client.responses.request_arguments["input"][1]["content"]
    assert "feedback_id" not in user_content


def test_openai_provider_rejects_empty_parsed_output() -> None:
    provider = OpenAIAnalysisProvider(
        api_key="test-key",
        model_name="test-model",
        timeout_seconds=10,
        client=FakeOpenAIClient(None),
    )

    with pytest.raises(AnalysisProviderError, match="no analysis result"):
        provider.analyze(
            FeedbackAnalysisRequest(feedback_id=1, content="결제가 실패합니다.")
        )
