from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
from bson import ObjectId

class PyObjectId(ObjectId):
    @classmethod
    def __get_validators__(cls):
        yield cls.validate

    @classmethod
    def validate(cls, v):
        if not ObjectId.is_valid(v):
            raise ValueError("Invalid objectid")
        return ObjectId(v)

    @classmethod
    def __modify_schema__(cls, field_schema):
        field_schema.update(type="string")

class NotificationBase(BaseModel):
    title: str
    message: str
    type: str  # info, success, warning, error
    read: bool = False
    recipient_id: str
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)

class NotificationCreate(NotificationBase):
    pass

class NotificationUpdate(BaseModel):
    title: Optional[str] = None
    message: Optional[str] = None
    type: Optional[str] = None
    read: Optional[bool] = None
    updated_at: datetime = Field(default_factory=datetime.utcnow)

class NotificationInDB(NotificationBase):
    id: PyObjectId = Field(default_factory=PyObjectId, alias="_id")

    class Config:
        allow_population_by_field_name = True
        arbitrary_types_allowed = True
        json_encoders = {ObjectId: str}