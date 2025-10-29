from fastapi import FastAPI
from .routes import notifications, email_notifications
from .database.database import connect_to_mongo, close_mongo_connection
from .config import settings

app = FastAPI(title=settings.PROJECT_NAME)

@app.on_event("startup")
async def startup_event():
    await connect_to_mongo()

@app.on_event("shutdown")
async def shutdown_event():
    await close_mongo_connection()

app.include_router(notifications.router, prefix=settings.API_V1_STR)
app.include_router(email_notifications.router, prefix=settings.API_V1_STR)

@app.get("/")
async def root():
    return {"message": "Notification Service API"}