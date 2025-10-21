from fastapi import APIRouter, HTTPException, status, Query
from typing import List, Optional
from datetime import datetime
from ..models.notification import NotificationCreate, NotificationUpdate, NotificationInDB
from ..database.database import db
from bson import ObjectId

router = APIRouter(prefix="/notifications", tags=["notifications"])

@router.post("/", response_model=NotificationInDB, status_code=status.HTTP_201_CREATED)
async def create_notification(notification: NotificationCreate):
    """Create a new notification"""
    notification_dict = notification.dict()
    result = await db.database["notifications"].insert_one(notification_dict)
    created_notification = await db.database["notifications"].find_one({"_id": result.inserted_id})
    return NotificationInDB(**created_notification)

@router.get("/", response_model=List[NotificationInDB])
async def get_notifications(
    recipient_id: Optional[str] = None,
    read: Optional[bool] = None,
    skip: int = 0,
    limit: int = Query(100, le=1000)
):
    """Get all notifications with optional filtering"""
    query = {}
    if recipient_id:
        query["recipient_id"] = recipient_id
    if read is not None:
        query["read"] = read
    
    notifications_cursor = db.database["notifications"].find(query).skip(skip).limit(limit)
    notifications = await notifications_cursor.to_list(length=limit)
    return [NotificationInDB(**notification) for notification in notifications]

@router.get("/unread-count", response_model=dict)
async def get_unread_count(recipient_id: str):
    """Get count of unread notifications for a recipient"""
    count = await db.database["notifications"].count_documents({
        "recipient_id": recipient_id,
        "read": False
    })
    return {"unread_count": count}

@router.get("/{notification_id}", response_model=NotificationInDB)
async def get_notification(notification_id: str):
    """Get a specific notification by ID"""
    if not ObjectId.is_valid(notification_id):
        raise HTTPException(status_code=400, detail="Invalid notification ID")
    
    notification = await db.database["notifications"].find_one({"_id": ObjectId(notification_id)})
    if notification is None:
        raise HTTPException(status_code=404, detail="Notification not found")
    return NotificationInDB(**notification)

@router.put("/{notification_id}", response_model=NotificationInDB)
async def update_notification(notification_id: str, notification_update: NotificationUpdate):
    """Update a specific notification"""
    if not ObjectId.is_valid(notification_id):
        raise HTTPException(status_code=400, detail="Invalid notification ID")
    
    update_data = {k: v for k, v in notification_update.dict().items() if v is not None}
    if not update_data:
        raise HTTPException(status_code=400, detail="No valid update data provided")
    
    result = await db.database["notifications"].update_one(
        {"_id": ObjectId(notification_id)},
        {"$set": update_data}
    )
    
    if result.modified_count == 0:
        raise HTTPException(status_code=404, detail="Notification not found")
    
    updated_notification = await db.database["notifications"].find_one({"_id": ObjectId(notification_id)})
    return NotificationInDB(**updated_notification)

@router.delete("/{notification_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_notification(notification_id: str):
    """Delete a specific notification"""
    if not ObjectId.is_valid(notification_id):
        raise HTTPException(status_code=400, detail="Invalid notification ID")
    
    result = await db.database["notifications"].delete_one({"_id": ObjectId(notification_id)})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Notification not found")

@router.post("/mark-as-read/{notification_id}", response_model=NotificationInDB)
async def mark_as_read(notification_id: str):
    """Mark a notification as read"""
    return await update_notification(notification_id, NotificationUpdate(read=True))

@router.post("/mark-as-unread/{notification_id}", response_model=NotificationInDB)
async def mark_as_unread(notification_id: str):
    """Mark a notification as unread"""
    return await update_notification(notification_id, NotificationUpdate(read=False))

@router.post("/mark-all-as-read", response_model=dict)
async def mark_all_as_read(recipient_id: str):
    """Mark all notifications as read for a recipient"""
    result = await db.database["notifications"].update_many(
        {"recipient_id": recipient_id},
        {"$set": {"read": True, "updated_at": datetime.utcnow()}}
    )
    return {"updated_count": result.modified_count}

@router.delete("/clear-all", status_code=status.HTTP_204_NO_CONTENT)
async def clear_all_notifications(recipient_id: str):
    """Delete all notifications for a recipient"""
    await db.database["notifications"].delete_many({"recipient_id": recipient_id})