from fastapi import APIRouter, HTTPException, status, Depends, Query
from typing import List, Optional
from datetime import datetime
from ..models.user import UserCreate, UserResponse, UserUpdate, UserInDB
from ..database.database import db
from ..utils.auth_utils import (
    hash_password, generate_temp_password, get_temp_password_expiry
)
from ..services.email_service import email_service
from ..routes.auth import get_current_user
from bson import ObjectId

router = APIRouter(prefix="/faculty", tags=["faculty"])

async def require_admin(current_user: UserInDB = Depends(get_current_user)) -> UserInDB:
    """Dependency to ensure user is an admin"""
    if current_user.role != "admin":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Admin access required"
        )
    return current_user

@router.post("/", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
async def create_faculty(
    faculty_data: UserCreate,
    admin: UserInDB = Depends(require_admin)
):
    """
    Create a new faculty member (Admin only)
    Generates temporary password and sends welcome email
    """
    # Check if email already exists
    existing_user = await db.database["users"].find_one({"email": faculty_data.email})
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email already registered"
        )
    
    # Generate temporary password
    temp_password = generate_temp_password()
    temp_password_hash = hash_password(temp_password)
    
    # Create user document
    user_dict = faculty_data.dict()
    user_dict.update({
        "password_hash": temp_password_hash,  # Initially same as temp password
        "temp_password_hash": temp_password_hash,
        "temp_password_expiry": get_temp_password_expiry(),
        "is_first_login": True,
        "password_change_required": True,
        "email_verified": False,
        "failed_login_attempts": 0,
        "account_locked": False,
        "created_at": datetime.utcnow(),
        "updated_at": datetime.utcnow()
    })
    
    # Insert into database
    result = await db.database["users"].insert_one(user_dict)
    
    # Send welcome email
    try:
        await email_service.send_welcome_email(
            recipient_email=faculty_data.email,
            recipient_name=faculty_data.name,
            temp_password=temp_password
        )
    except Exception as e:
        print(f"Warning: Failed to send welcome email: {str(e)}")
        # Don't fail the request if email fails
    
    # Fetch created user
    created_user = await db.database["users"].find_one({"_id": result.inserted_id})
    created_user_obj = UserInDB(**created_user)
    
    return UserResponse(
        _id=str(created_user_obj.id),
        name=created_user_obj.name,
        email=created_user_obj.email,
        phone=created_user_obj.phone,
        department=created_user_obj.department,
        designation=created_user_obj.designation,
        employee_id=created_user_obj.employee_id,
        role=created_user_obj.role,
        bio=created_user_obj.bio,
        profile_picture=created_user_obj.profile_picture,
        is_first_login=created_user_obj.is_first_login,
        password_change_required=created_user_obj.password_change_required,
        email_verified=created_user_obj.email_verified,
        last_password_change=created_user_obj.last_password_change,
        created_at=created_user_obj.created_at
    )

@router.get("/", response_model=List[UserResponse])
async def get_all_faculty(
    department: Optional[str] = None,
    designation: Optional[str] = None,
    skip: int = 0,
    limit: int = Query(100, le=1000),
    admin: UserInDB = Depends(require_admin)
):
    """Get all faculty members with optional filtering (Admin only)"""
    query = {"role": "faculty"}
    
    if department:
        query["department"] = department
    if designation:
        query["designation"] = designation
    
    faculty_cursor = db.database["users"].find(query).skip(skip).limit(limit)
    faculty_list = await faculty_cursor.to_list(length=limit)
    
    return [
        UserResponse(
            _id=str(user["_id"]),
            name=user["name"],
            email=user["email"],
            phone=user.get("phone"),
            department=user["department"],
            designation=user["designation"],
            employee_id=user.get("employee_id"),
            role=user["role"],
            bio=user.get("bio"),
            profile_picture=user.get("profile_picture"),
            is_first_login=user["is_first_login"],
            password_change_required=user["password_change_required"],
            email_verified=user["email_verified"],
            last_password_change=user.get("last_password_change"),
            created_at=user["created_at"]
        )
        for user in faculty_list
    ]

@router.get("/pending-setup", response_model=List[UserResponse])
async def get_pending_setup_faculty(
    admin: UserInDB = Depends(require_admin)
):
    """Get faculty members who haven't completed first-time setup (Admin only)"""
    query = {
        "role": "faculty",
        "is_first_login": True
    }
    
    faculty_cursor = db.database["users"].find(query)
    faculty_list = await faculty_cursor.to_list(length=None)
    
    return [
        UserResponse(
            _id=str(user["_id"]),
            name=user["name"],
            email=user["email"],
            phone=user.get("phone"),
            department=user["department"],
            designation=user["designation"],
            employee_id=user.get("employee_id"),
            role=user["role"],
            bio=user.get("bio"),
            profile_picture=user.get("profile_picture"),
            is_first_login=user["is_first_login"],
            password_change_required=user["password_change_required"],
            email_verified=user["email_verified"],
            last_password_change=user.get("last_password_change"),
            created_at=user["created_at"]
        )
        for user in faculty_list
    ]

@router.get("/{faculty_id}", response_model=UserResponse)
async def get_faculty(
    faculty_id: str,
    admin: UserInDB = Depends(require_admin)
):
    """Get specific faculty member details (Admin only)"""
    if not ObjectId.is_valid(faculty_id):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid faculty ID"
        )
    
    user = await db.database["users"].find_one({"_id": ObjectId(faculty_id)})
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Faculty not found"
        )
    
    user_obj = UserInDB(**user)
    
    return UserResponse(
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

@router.put("/{faculty_id}", response_model=UserResponse)
async def update_faculty(
    faculty_id: str,
    faculty_update: UserUpdate,
    admin: UserInDB = Depends(require_admin)
):
    """Update faculty member information (Admin only)"""
    if not ObjectId.is_valid(faculty_id):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid faculty ID"
        )
    
    update_data = {k: v for k, v in faculty_update.dict().items() if v is not None}
    if not update_data:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No valid update data provided"
        )
    
    result = await db.database["users"].update_one(
        {"_id": ObjectId(faculty_id)},
        {"$set": update_data}
    )
    
    if result.modified_count == 0:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Faculty not found"
        )
    
    updated_user = await db.database["users"].find_one({"_id": ObjectId(faculty_id)})
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

@router.delete("/{faculty_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_faculty(
    faculty_id: str,
    admin: UserInDB = Depends(require_admin)
):
    """Delete faculty member (Admin only)"""
    if not ObjectId.is_valid(faculty_id):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid faculty ID"
        )
    
    result = await db.database["users"].delete_one({"_id": ObjectId(faculty_id)})
    if result.deleted_count == 0:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Faculty not found"
        )

@router.post("/{faculty_id}/resend-credentials")
async def resend_credentials(
    faculty_id: str,
    admin: UserInDB = Depends(require_admin)
):
    """Resend welcome email with credentials to faculty (Admin only)"""
    if not ObjectId.is_valid(faculty_id):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid faculty ID"
        )
    
    user = await db.database["users"].find_one({"_id": ObjectId(faculty_id)})
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Faculty not found"
        )
    
    user_obj = UserInDB(**user)
    
    # Generate new temporary password
    temp_password = generate_temp_password()
    temp_password_hash = hash_password(temp_password)
    
    # Update user with new temp password
    await db.database["users"].update_one(
        {"_id": ObjectId(faculty_id)},
        {
            "$set": {
                "temp_password_hash": temp_password_hash,
                "temp_password_expiry": get_temp_password_expiry(),
                "updated_at": datetime.utcnow()
            }
        }
    )
    
    # Send email
    try:
        await email_service.send_credentials_resend_email(
            recipient_email=user_obj.email,
            recipient_name=user_obj.name,
            temp_password=temp_password
        )
        return {"message": "Credentials sent successfully"}
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to send email: {str(e)}"
        )
