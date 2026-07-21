from typing import Protocol

from app.schemas import FeedbackAnalysisRequest, ProviderAnalysis


class AnalysisProvider(Protocol):
    @property
    def model_name(self) -> str:
        ...

    def analyze(self, request: FeedbackAnalysisRequest) -> ProviderAnalysis:
        ...


class AnalysisProviderError(RuntimeError):
    pass
