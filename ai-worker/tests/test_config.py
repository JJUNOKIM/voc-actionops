import pytest
from pydantic import ValidationError

from app.config import Settings


def test_openai_provider_requires_api_key() -> None:
    with pytest.raises(ValidationError, match="OPENAI_API_KEY"):
        Settings(
            _env_file=None,
            ai_provider="openai",
            ai_worker_api_key="test-internal-key",
            openai_api_key=None,
        )


def test_deterministic_provider_does_not_require_openai_key() -> None:
    settings = Settings(
        _env_file=None,
        ai_provider="deterministic",
        ai_worker_api_key="test-internal-key",
    )

    assert settings.openai_api_key is None


def test_internal_api_key_must_be_long_enough() -> None:
    with pytest.raises(ValidationError, match="at least 16 characters"):
        Settings(
            _env_file=None,
            ai_provider="deterministic",
            ai_worker_api_key="short-key",
        )
