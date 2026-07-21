from typing import Literal

from pydantic import Field, SecretStr, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ai_provider: Literal["deterministic", "openai"] = "deterministic"
    ai_worker_api_key: SecretStr
    openai_api_key: SecretStr | None = None
    openai_model: str = Field(default="gpt-5.6-luna", min_length=1, max_length=100)
    openai_timeout_seconds: float = Field(default=30.0, gt=0, le=120)

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @field_validator("ai_worker_api_key")
    @classmethod
    def validate_internal_api_key(cls, value: SecretStr) -> SecretStr:
        normalized = value.get_secret_value().strip()
        if len(normalized) < 16:
            raise ValueError("AI_WORKER_API_KEY must be at least 16 characters")
        return SecretStr(normalized)

    @model_validator(mode="after")
    def require_provider_credentials(self) -> "Settings":
        if self.ai_provider == "openai" and (
            self.openai_api_key is None
            or not self.openai_api_key.get_secret_value().strip()
        ):
            raise ValueError("OPENAI_API_KEY is required when AI_PROVIDER is openai")
        return self
