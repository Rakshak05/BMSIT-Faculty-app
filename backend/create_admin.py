"""
Script to create a default admin user for the BMSIT Faculty Portal
Run this script once to set up the initial admin account
"""
from pymongo import MongoClient
from passlib.context import CryptContext
from datetime import datetime

from app.config import settings

# Configuration from settings
MONGODB_URL = settings.MONGODB_URL
MONGODB_DATABASE = settings.MONGODB_DATABASE

# Admin credentials from environment (fallback for local dev only)
import os
ADMIN_EMAIL = os.getenv("ADMIN_EMAIL", "admin@bmsit.in")
ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "Admin@123")
ADMIN_NAME = "System Administrator"

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

def create_admin():
    """Create default admin user"""
    try:
        client = MongoClient(MONGODB_URL, serverSelectionTimeoutMS=5000)
        
        # Test connection
        client.server_info()
        print("✅ Connected to MongoDB")
        
        db = client[MONGODB_DATABASE]
        
        # Check if admin already exists
        existing_admin = db.users.find_one({"email": ADMIN_EMAIL})
        if existing_admin:
            print(f"❌ Admin user with email {ADMIN_EMAIL} already exists!")
            return
        
        # Create admin user
        admin_user = {
            "name": ADMIN_NAME,
            "email": ADMIN_EMAIL,
            "phone": None,
            "department": "Administration",
            "designation": "System Administrator",
            "employee_id": "ADMIN001",
            "role": "admin",
            "bio": None,
            "profile_picture": None,
            "password_hash": pwd_context.hash(ADMIN_PASSWORD),
            "is_first_login": False,  # Admin doesn't need first-time setup
            "password_change_required": True,  # But should change password
            "temp_password_hash": None,
            "temp_password_expiry": None,
            "email_verified": True,
            "last_password_change": datetime.utcnow(),
            "failed_login_attempts": 0,
            "account_locked": False,
            "created_at": datetime.utcnow(),
            "updated_at": datetime.utcnow()
        }
        
        result = db.users.insert_one(admin_user)
        
        print("\n✅ Admin user created successfully!")
        print(f"   Email: {ADMIN_EMAIL}")
        print(f"   Password: {ADMIN_PASSWORD}")
        print("\n⚠️  IMPORTANT: Please change the admin password after first login!")
        print("\nYou can now start the backend server and login at http://localhost:5173/login")
        
    except Exception as e:
        print(f"\n❌ Error: {str(e)}")
        print("\nPossible issues:")
        print("1. MongoDB is not running - Start MongoDB service")
        print("2. Connection refused - Check if MongoDB is running on localhost:27017")
        print("3. Missing dependencies - Run: pip install pymongo passlib[bcrypt]")
    finally:
        try:
            client.close()
        except:
            pass

if __name__ == "__main__":
    print("=" * 60)
    print("BMSIT Faculty Portal - Admin User Setup")
    print("=" * 60)
    print("\nCreating default admin user...")
    create_admin()
    print("\n" + "=" * 60)
