from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional
import os
from pathlib import Path

# Get root directory (2 levels up from backend/app/config.py)
ROOT_DIR = Path(__file__).resolve().parent.parent.parent
ENV_FILE = ROOT_DIR / ".env"

class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=str(ENV_FILE) if ENV_FILE.exists() else None,
        env_file_encoding='utf-8',
        extra='ignore'
    )

    PROJECT_NAME: str = "BMSIT Faculty Portal"
    MONGODB_URL: str
    MONGODB_DATABASE: str
    API_V1_STR: str = "/api/v1"
    
    # JWT Settings
    SECRET_KEY: str
    ALGORITHM: str
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24  # 24 hours
    
    # Password Policy
    TEMP_PASSWORD_EXPIRY_DAYS: int = 7
    MIN_PASSWORD_LENGTH: int = 8
    
    # Email Settings
    USE_MOCK_EMAIL: bool = True
    SMTP_HOST: str
    SMTP_PORT: int
    SMTP_USERNAME: Optional[str] = None
    SMTP_PASSWORD: Optional[str] = None
    SMTP_SENDER_EMAIL: str
    SMTP_USE_TLS: bool = True

settings = Settings()