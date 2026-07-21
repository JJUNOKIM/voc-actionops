from enum import Enum

from pydantic import BaseModel, ConfigDict, Field, field_validator


class Sentiment(str, Enum):
    POSITIVE = "POSITIVE"
    NEUTRAL = "NEUTRAL"
    NEGATIVE = "NEGATIVE"


class FeedbackAnalysisRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    feedback_id: int = Field(gt=0)
    content: str = Field(min_length=1, max_length=20_000)
    rating: float | None = Field(default=None, ge=0, le=5)
    language: str | None = Field(default=None, max_length=20)
    product_name: str | None = Field(default=None, max_length=150)
    customer_segment: str | None = Field(default=None, max_length=100)

    @field_validator("content")
    @classmethod
    def content_must_not_be_blank(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("content must not be blank")
        return value


class ProviderAnalysis(BaseModel):
    model_config = ConfigDict(extra="forbid")

    sentiment: Sentiment
    sentiment_score: float = Field(ge=-1, le=1)
    category: str = Field(min_length=1, max_length=100)
    urgency_score: float = Field(ge=0, le=1)
    summary: str = Field(min_length=1, max_length=1000)
    confidence_score: float = Field(ge=0, le=1)


class FeedbackAnalysisResponse(ProviderAnalysis):
    feedback_id: int = Field(gt=0)
    model_name: str = Field(min_length=1, max_length=100)


class HealthResponse(BaseModel):
    status: str
    provider: str


class ErrorResponse(BaseModel):
    code: str
    message: str
