from pydantic import BaseModel, Field, EmailStr
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

class UserBase(BaseModel):
    name: str
    email: EmailStr
    phone: Optional[str] = None
    department: str
    designation: str
    employee_id: Optional[str] = None
    role: str = "faculty"  # admin or faculty
    bio: Optional[str] = None
    profile_picture: Optional[str] = None

class UserCreate(UserBase):
    """Used when admin creates a new faculty member"""
    pass

class UserInDB(UserBase):
    id: PyObjectId = Field(default_factory=PyObjectId, alias="_id")
    password_hash: str
    is_first_login: bool = True
    password_change_required: bool = True
    temp_password_hash: Optional[str] = None
    temp_password_expiry: Optional[datetime] = None
    email_verified: bool = False
    last_password_change: Optional[datetime] = None
    failed_login_attempts: int = 0
    account_locked: bool = False
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)

    class Config:
        allow_population_by_field_name = True
        arbitrary_types_allowed = True
        json_encoders = {ObjectId: str}

class UserResponse(UserBase):
    """User data returned to frontend (no sensitive info)"""
    id: str = Field(alias="_id")
    is_first_login: bool
    password_change_required: bool
    email_verified: bool
    last_password_change: Optional[datetime]
    created_at: datetime

    class Config:
        allow_population_by_field_name = True

class LoginRequest(BaseModel):
    email: EmailStr
    password: str

class LoginResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserResponse

class FirstTimeSetupRequest(BaseModel):
    temp_password: str
    new_password: str
    phone: Optional[str] = None
    bio: Optional[str] = None

class ChangePasswordRequest(BaseModel):
    old_password: str
    new_password: str

class UserUpdate(BaseModel):
    name: Optional[str] = None
    phone: Optional[str] = None
    department: Optional[str] = None
    designation: Optional[str] = None
    bio: Optional[str] = None
    profile_picture: Optional[str] = None
    updated_at: datetime = Field(default_factory=datetime.utcnow)
