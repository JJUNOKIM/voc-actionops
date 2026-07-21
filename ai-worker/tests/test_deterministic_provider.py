import pytest
from pydantic import ValidationError

from app.providers.deterministic import DeterministicAnalysisProvider
from app.schemas import FeedbackAnalysisRequest, ProviderAnalysis, Sentiment


def test_same_input_produces_same_result() -> None:
    provider = DeterministicAnalysisProvider()
    request = FeedbackAnalysisRequest(
        feedback_id=1,
        content="로그인 오류가 반복돼서 너무 불편합니다.",
        rating=1,
        language="ko",
    )

    assert provider.analyze(request) == provider.analyze(request)


def test_keyword_and_rating_drive_analysis() -> None:
    provider = DeterministicAnalysisProvider()
    request = FeedbackAnalysisRequest(
        feedback_id=1,
        content="배송이 빨라서 만족합니다.",
        rating=5,
        language="ko",
    )

    result = provider.analyze(request)

    assert result.sentiment == Sentiment.POSITIVE
    assert result.sentiment_score == 1
    assert result.category == "DELIVERY"
    assert result.urgency_score == 0.35


def test_long_content_is_summarized_within_contract() -> None:
    provider = DeterministicAnalysisProvider()
    request = FeedbackAnalysisRequest(
        feedback_id=1,
        content="결제 화면이 너무 복잡합니다. " * 20,
    )

    result = provider.analyze(request)

    assert len(result.summary) == 160
    assert result.summary.endswith("...")


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("sentiment_score", -1.01),
        ("urgency_score", 1.01),
        ("confidence_score", -0.01),
    ],
)
def test_result_rejects_scores_outside_backend_range(
    field: str,
    value: float,
) -> None:
    payload = {
        "sentiment": Sentiment.NEUTRAL,
        "sentiment_score": 0,
        "category": "OTHER",
        "urgency_score": 0.5,
        "summary": "summary",
        "confidence_score": 0.5,
    }
    payload[field] = value

    with pytest.raises(ValidationError):
        ProviderAnalysis(**payload)
