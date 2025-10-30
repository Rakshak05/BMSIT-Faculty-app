from motor.motor_asyncio import AsyncIOMotorClient
from ..config import settings

class Database:
    client: AsyncIOMotorClient = None
    database = None

db = Database()

async def connect_to_mongo():
    db.client = AsyncIOMotorClient(settings.MONGODB_URL)
    db.database = db.client[settings.MONGODB_DATABASE]
    print("Connected to MongoDB")

async def close_mongo_connection():
    db.client.close()
    print("Closed MongoDB connection")