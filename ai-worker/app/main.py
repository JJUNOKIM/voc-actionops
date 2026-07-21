import secrets

from fastapi import Depends, FastAPI, Header, Request, status
from fastapi.responses import JSONResponse

from app.config import Settings
from app.providers import (
    AnalysisProvider,
    AnalysisProviderError,
    DeterministicAnalysisProvider,
    OpenAIAnalysisProvider,
)
from app.schemas import (
    ErrorResponse,
    FeedbackAnalysisRequest,
    FeedbackAnalysisResponse,
    HealthResponse,
)


def build_provider(settings: Settings) -> AnalysisProvider:
    if settings.ai_provider == "openai":
        if settings.openai_api_key is None:
            raise ValueError("OPENAI_API_KEY is required when AI_PROVIDER is openai")
        return OpenAIAnalysisProvider(
            api_key=settings.openai_api_key.get_secret_value(),
            model_name=settings.openai_model,
            timeout_seconds=settings.openai_timeout_seconds,
        )
    return DeterministicAnalysisProvider()


def create_app(
    settings: Settings | None = None,
    provider: AnalysisProvider | None = None,
) -> FastAPI:
    resolved_settings = settings or Settings()
    resolved_provider = provider or build_provider(resolved_settings)

    app = FastAPI(
        title="VOC ActionOps AI Worker",
        version="0.1.0",
        docs_url="/docs",
        redoc_url=None,
    )
    app.state.settings = resolved_settings
    app.state.provider = resolved_provider

    @app.exception_handler(AnalysisProviderError)
    async def handle_provider_error(
        _request: Request,
        exception: AnalysisProviderError,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=status.HTTP_502_BAD_GATEWAY,
            content=ErrorResponse(
                code="ANALYSIS_PROVIDER_ERROR",
                message=str(exception),
            ).model_dump(),
        )

    def verify_internal_api_key(
        request: Request,
        x_internal_api_key: str | None = Header(default=None),
    ) -> None:
        expected = request.app.state.settings.ai_worker_api_key.get_secret_value()
        if x_internal_api_key is None or not secrets.compare_digest(
            x_internal_api_key,
            expected,
        ):
            raise UnauthorizedInternalRequest()

    @app.exception_handler(UnauthorizedInternalRequest)
    async def handle_unauthorized_request(
        _request: Request,
        _exception: UnauthorizedInternalRequest,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=status.HTTP_401_UNAUTHORIZED,
            content=ErrorResponse(
                code="UNAUTHORIZED",
                message="A valid internal API key is required",
            ).model_dump(),
        )

    @app.get("/health", response_model=HealthResponse)
    def health() -> HealthResponse:
        return HealthResponse(
            status="UP",
            provider=resolved_settings.ai_provider,
        )

    @app.post(
        "/internal/v1/feedback-analysis",
        response_model=FeedbackAnalysisResponse,
        responses={
            401: {"model": ErrorResponse},
            502: {"model": ErrorResponse},
        },
        dependencies=[Depends(verify_internal_api_key)],
    )
    def analyze_feedback(
        payload: FeedbackAnalysisRequest,
        request: Request,
    ) -> FeedbackAnalysisResponse:
        active_provider: AnalysisProvider = request.app.state.provider
        result = active_provider.analyze(payload)
        return FeedbackAnalysisResponse(
            feedback_id=payload.feedback_id,
            model_name=active_provider.model_name,
            **result.model_dump(),
        )

    return app


class UnauthorizedInternalRequest(Exception):
    pass


app = create_app()
