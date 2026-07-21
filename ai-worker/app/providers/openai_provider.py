from openai import OpenAI, OpenAIError

from app.providers.base import AnalysisProviderError
from app.schemas import FeedbackAnalysisRequest, ProviderAnalysis


class OpenAIAnalysisProvider:
    _SYSTEM_PROMPT = """You analyze customer feedback for an operations team.
Return sentiment, category, urgency, a concise summary, and confidence.
Use an uppercase English category of at most 100 characters.
sentiment_score must be between -1 and 1. urgency_score and confidence_score
must be between 0 and 1. Base the result only on the supplied feedback."""

    def __init__(
        self,
        api_key: str,
        model_name: str,
        timeout_seconds: float,
        client: OpenAI | None = None,
    ) -> None:
        self._model_name = model_name
        self._client = client or OpenAI(api_key=api_key, timeout=timeout_seconds)

    @property
    def model_name(self) -> str:
        return self._model_name

    def analyze(self, request: FeedbackAnalysisRequest) -> ProviderAnalysis:
        try:
            response = self._client.responses.parse(
                model=self._model_name,
                input=[
                    {"role": "system", "content": self._SYSTEM_PROMPT},
                    {
                        "role": "user",
                        "content": request.model_dump_json(exclude={"feedback_id"}),
                    },
                ],
                text_format=ProviderAnalysis,
            )
        except OpenAIError as exception:
            raise AnalysisProviderError("OpenAI analysis request failed") from exception

        if response.output_parsed is None:
            raise AnalysisProviderError("OpenAI returned no analysis result")
        return response.output_parsed
