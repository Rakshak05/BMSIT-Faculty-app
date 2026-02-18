from fastapi import APIRouter, HTTPException, status, Depends, Header
from typing import Optional
from datetime import datetime
from ..models.user import (
    LoginRequest, LoginResponse, FirstTimeSetupRequest,
    ChangePasswordRequest, UserResponse, UserInDB
)
from ..database.database import db
from ..utils.auth_utils import (
    verify_password, hash_password, create_access_token,
    decode_access_token, validate_password_strength
)
from bson import ObjectId
from pydantic import BaseModel, Field

router = APIRouter(prefix="/auth", tags=["authentication"])


class UserUpdate(BaseModel):
    name: Optional[str] = None
    phone: Optional[str] = None
    department: Optional[str] = None
    designation: Optional[str] = None
    bio: Optional[str] = None
    profile_picture: Optional[str] = None
    updated_at: datetime = Field(default_factory=datetime.utcnow)


async def get_current_user(authorization: Optional[str] = Header(None)) -> UserInDB:
    """Dependency to get current authenticated user from JWT token"""
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Not authenticated",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    token = authorization.replace("Bearer ", "")
    payload = decode_access_token(token)
    
    if payload is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    
    user_id = payload.get("sub")
    if user_id is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token payload"
        )
    
    user = await db.database["users"].find_one({"_id": ObjectId(user_id)})
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User not found"
        )
    
    return UserInDB(**user)

@router.post("/login", response_model=LoginResponse)
async def login(login_data: LoginRequest):
    """
    Login endpoint - handles both temporary and regular passwords
    Returns JWT token and user info
    """
    # Find user by email
    user = await db.database["users"].find_one({"email": login_data.email})
    
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password"
        )
    
    user_obj = UserInDB(**user)
    
    # Check if account is locked
    if user_obj.account_locked:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account is locked. Please contact administrator."
        )
    
    # Try to verify with regular password first
    password_valid = verify_password(login_data.password, user_obj.password_hash)
    
    # If regular password fails and user has temp password, try temp password
    if not password_valid and user_obj.temp_password_hash:
        # Check if temp password is expired
        if user_obj.temp_password_expiry and user_obj.temp_password_expiry < datetime.utcnow():
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Temporary password has expired. Please contact administrator."
            )
        
        password_valid = verify_password(login_data.password, user_obj.temp_password_hash)
    
    if not password_valid:
        # Increment failed login attempts
        await db.database["users"].update_one(
            {"_id": user_obj.id},
            {
                "$inc": {"failed_login_attempts": 1},
                "$set": {"updated_at": datetime.utcnow()}
            }
        )
        
        # Lock account after 5 failed attempts
        if user_obj.failed_login_attempts >= 4:
            await db.database["users"].update_one(
                {"_id": user_obj.id},
                {"$set": {"account_locked": True, "updated_at": datetime.utcnow()}}
            )
        
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password"
        )
    
    # Reset failed login attempts on successful login
    await db.database["users"].update_one(
        {"_id": user_obj.id},
        {
            "$set": {
                "failed_login_attempts": 0,
                "updated_at": datetime.utcnow()
            }
        }
    )
    
    # Create access token
    access_token = create_access_token(data={"sub": str(user_obj.id)})
    
    # Prepare user response
    user_response = UserResponse(
        _id=str(user_obj.id),
        name=user_obj.name,
        email=user_obj.email,
        phone=user_obj.phone,
        department=user_obj.department,
        designation=user_obj.designation,
        employee_id=user_obj.employee_id,
        role=user_obj.role,
        bio=user_obj.bio,
        profile_picture=user_obj.profile_picture,
        is_first_login=user_obj.is_first_login,
        password_change_required=user_obj.password_change_required,
        email_verified=user_obj.email_verified,
        last_password_change=user_obj.last_password_change,
        created_at=user_obj.created_at
    )
    
    return LoginResponse(
        access_token=access_token,
        user=user_response
    )

@router.post("/first-time-setup", response_model=UserResponse)
async def first_time_setup(
    setup_data: FirstTimeSetupRequest,
    current_user: UserInDB = Depends(get_current_user)
):
    """
    Complete first-time setup - verify temp password and set new password
    """
    if not current_user.is_first_login:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="First-time setup already completed"
        )
    
    # Verify temporary password
    if not current_user.temp_password_hash:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No temporary password found"
        )
    
    if not verify_password(setup_data.temp_password, current_user.temp_password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid temporary password"
        )
    
    # Validate new password strength
    is_valid, error_msg = validate_password_strength(setup_data.new_password)
    if not is_valid:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=error_msg
        )
    
    # Update user with new password and optional profile data
    update_data = {
        "password_hash": hash_password(setup_data.new_password),
        "is_first_login": False,
        "password_change_required": False,
        "temp_password_hash": None,
        "temp_password_expiry": None,
        "last_password_change": datetime.utcnow(),
        "updated_at": datetime.utcnow()
    }
    
    if setup_data.phone:
        update_data["phone"] = setup_data.phone
    if setup_data.bio:
        update_data["bio"] = setup_data.bio
    
    await db.database["users"].update_one(
        {"_id": current_user.id},
        {"$set": update_data}
    )
    
    # Fetch updated user
    updated_user = await db.database["users"].find_one({"_id": current_user.id})
    updated_user_obj = UserInDB(**updated_user)
    
    return UserResponse(
        _id=str(updated_user_obj.id),
        name=updated_user_obj.name,
        email=updated_user_obj.email,
        phone=updated_user_obj.phone,
        department=updated_user_obj.department,
        designation=updated_user_obj.designation,
        employee_id=updated_user_obj.employee_id,
        role=updated_user_obj.role,
        bio=updated_user_obj.bio,
        profile_picture=updated_user_obj.profile_picture,
        is_first_login=updated_user_obj.is_first_login,
        password_change_required=updated_user_obj.password_change_required,
        email_verified=updated_user_obj.email_verified,
        last_password_change=updated_user_obj.last_password_change,
        created_at=updated_user_obj.created_at
    )

@router.post("/change-password")
async def change_password(
    password_data: ChangePasswordRequest,
    current_user: UserInDB = Depends(get_current_user)
):
    """Change password for existing users"""
    # Verify old password
    if not verify_password(password_data.old_password, current_user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid current password"
        )
    
    # Validate new password strength
    is_valid, error_msg = validate_password_strength(password_data.new_password)
    if not is_valid:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=error_msg
        )
    
    # Update password
    await db.database["users"].update_one(
        {"_id": current_user.id},
        {
            "$set": {
                "password_hash": hash_password(password_data.new_password),
                "last_password_change": datetime.utcnow(),
                "updated_at": datetime.utcnow()
            }
        }
    )
    
    return {"message": "Password changed successfully"}

@router.get("/me", response_model=UserResponse)
async def get_current_user_info(current_user: UserInDB = Depends(get_current_user)):
    """Get current authenticated user information"""
    return UserResponse(
        _id=str(current_user.id),
        name=current_user.name,
        email=current_user.email,
        phone=current_user.phone,
        department=current_user.department,
        designation=current_user.designation,
        employee_id=current_user.employee_id,
        role=current_user.role,
        bio=current_user.bio,
        profile_picture=current_user.profile_picture,
        is_first_login=current_user.is_first_login,
        password_change_required=current_user.password_change_required,
        email_verified=current_user.email_verified,
        last_password_change=current_user.last_password_change,
        created_at=current_user.created_at
    )

@router.post("/verify-token")
async def verify_token(current_user: UserInDB = Depends(get_current_user)):
    """Verify if the provided token is valid"""
    return {"valid": True, "user_id": str(current_user.id)}

@router.put("/update-profile", response_model=UserResponse)
async def update_user_profile(
    update_data: UserUpdate,
    current_user: UserInDB = Depends(get_current_user)
):
    """Update user profile information"""
    # Filter out None values
    update_dict = {k: v for k, v in update_data.dict().items() if v is not None}
    
    if not update_dict:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No valid fields to update"
        )
    
    # Add updated_at timestamp
    update_dict["updated_at"] = datetime.utcnow()
    
    # Update user in database
    await db.database["users"].update_one(
        {"_id": current_user.id},
        {"$set": update_dict}
    )
    
    # Fetch updated user
    updated_user = await db.database["users"].find_one({"_id": current_user.id})
    updated_user_obj = UserInDB(**updated_user)
    
    return UserResponse(
        _id=str(updated_user_obj.id),
        name=updated_user_obj.name,
        email=updated_user_obj.email,
        phone=updated_user_obj.phone,
        department=updated_user_obj.department,
        designation=updated_user_obj.designation,
        employee_id=updated_user_obj.employee_id,
        role=updated_user_obj.role,
        bio=updated_user_obj.bio,
        profile_picture=updated_user_obj.profile_picture,
        is_first_login=updated_user_obj.is_first_login,
        password_change_required=updated_user_obj.password_change_required,
        email_verified=updated_user_obj.email_verified,
        last_password_change=updated_user_obj.last_password_change,
        created_at=updated_user_obj.created_at
    )
