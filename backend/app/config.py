from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    PROJECT_NAME: str = "BMSIT Faculty Portal"
    MONGODB_URL: str = "mongodb://localhost:27017"
    MONGODB_DATABASE: str = "bmsit_faculty_db"
    API_V1_STR: str = "/api/v1"
    
    # JWT Settings
    SECRET_KEY: str = "your-secret-key-change-this-in-production-min-32-chars"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24  # 24 hours
    
    # Password Policy
    TEMP_PASSWORD_EXPIRY_DAYS: int = 7
    MIN_PASSWORD_LENGTH: int = 8
    
    # Email Settings
    USE_MOCK_EMAIL: bool = True  # Set to False to use real SMTP
    SMTP_HOST: str = "smtp.gmail.com"
    SMTP_PORT: int = 587
    SMTP_USERNAME: Optional[str] = None
    SMTP_PASSWORD: Optional[str] = None
    SMTP_SENDER_EMAIL: str = "noreply@bmsit.in"
    SMTP_USE_TLS: bool = True

settings = Settings()