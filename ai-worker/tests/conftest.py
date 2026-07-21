import os

import pytest
from fastapi.testclient import TestClient

os.environ.setdefault("AI_WORKER_API_KEY", "test-internal-key")

from app.config import Settings
from app.main import create_app
from app.providers.deterministic import DeterministicAnalysisProvider


@pytest.fixture
def settings() -> Settings:
    return Settings(
        _env_file=None,
        ai_provider="deterministic",
        ai_worker_api_key="test-internal-key",
    )


@pytest.fixture
def client(settings: Settings) -> TestClient:
    app = create_app(
        settings=settings,
        provider=DeterministicAnalysisProvider(),
    )
    return TestClient(app)
