from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    PROJECT_NAME: str = "Notification Service"
    MONGODB_URL: str = "mongodb://localhost:27017"
    MONGODB_DATABASE: str = "notifications_db"
    API_V1_STR: str = "/api/v1"

settings = Settings()