from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from .routes import notifications, auth, faculty
from .database.database import connect_to_mongo, close_mongo_connection
from .config import settings

app = FastAPI(title=settings.PROJECT_NAME)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://localhost:3000"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
async def startup_event():
    await connect_to_mongo()

@app.on_event("shutdown")
async def shutdown_event():
    await close_mongo_connection()

# Register routers
app.include_router(auth.router, prefix=settings.API_V1_STR)
app.include_router(faculty.router, prefix=settings.API_V1_STR)
app.include_router(notifications.router, prefix=settings.API_V1_STR)

@app.get("/")
async def root():
    return {"message": f"{settings.PROJECT_NAME} API", "version": "1.0.0"}