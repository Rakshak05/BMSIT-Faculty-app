# Faculty Onboarding System - Setup Guide

This guide explains how to set up and use the ADMIN-managed faculty onboarding system.

## Overview

The system implements a secure workflow where:
1. **Admins** create faculty profiles through the web interface
2. **System** generates temporary passwords and sends welcome emails
3. **Faculty** receive emails with temporary credentials
4. **Faculty** complete first-time setup (change password, update profile)
5. **Faculty** access the portal with their new credentials

## Prerequisites

- Python 3.8+ (for backend)
- Node.js 16+ (for frontend)
- MongoDB (running locally or remote)

## Installation

### Backend Setup

1. Navigate to the backend directory:
```bash
cd backend
```

2. Install Python dependencies:
```bash
pip install -r requirements.txt
```

3. Create a default admin user:
```bash
python create_admin.py
```

This will create an admin account with:
- **Email**: `admin@bmsit.in`
- **Password**: `Admin@123`
- ⚠️ **Change this password after first login!**

4. Start the backend server:
```bash
cd app
uvicorn main:app --reload --port 8000
```

The API will be available at `http://localhost:8000`

### Frontend Setup

1. Navigate to the frontend directory:
```bash
cd frontend
```

2. Install dependencies:
```bash
npm install
```

3. Start the development server:
```bash
npm run dev
```

The frontend will be available at `http://localhost:5173`

## Configuration

### Email Service

By default, the system uses a **mock email service** that logs emails to the console instead of sending them. This is perfect for development and testing.

To enable real email sending:

1. Edit `backend/app/config.py`:
```python
USE_MOCK_EMAIL: bool = False  # Change to False
SMTP_HOST: str = "smtp.gmail.com"
SMTP_PORT: int = 587
SMTP_USERNAME: str = "your-email@gmail.com"
SMTP_PASSWORD: str = "your-app-password"
SMTP_SENDER_EMAIL: str = "noreply@bmsit.in"
```

2. For Gmail, you'll need to:
   - Enable 2-factor authentication
   - Generate an "App Password" (not your regular password)
   - Use the app password in `SMTP_PASSWORD`

### JWT Secret Key

For production, change the JWT secret key in `backend/app/config.py`:
```python
SECRET_KEY: str = "your-very-secure-secret-key-min-32-characters"
```

### MongoDB Connection

Update MongoDB connection in `backend/app/config.py` if needed:
```python
MONGODB_URL: str = "mongodb://localhost:27017"
MONGODB_DATABASE: str = "bmsit_faculty_db"
```

## Usage

### Admin Workflow

1. **Login as Admin**
   - Go to `http://localhost:5173/login`
   - Email: `admin@bmsit.in`
   - Password: `Admin@123` (or your changed password)

2. **Add New Faculty**
   - Navigate to "Add Faculty" page
   - Fill in faculty details:
     - Full Name (required)
     - Email (required, must end with @bmsit.in)
     - Department (required)
     - Designation (required)
     - Employee ID (optional)
     - Phone Number (optional)
   - Click "Add Member"
   - System will:
     - Create the faculty account
     - Generate a temporary password
     - Send a welcome email (or log it to console in mock mode)

3. **View Pending Setups**
   - API endpoint: `GET /api/v1/faculty/pending-setup`
   - Shows all faculty who haven't completed first-time setup

4. **Resend Credentials**
   - API endpoint: `POST /api/v1/faculty/{id}/resend-credentials`
   - Generates new temporary password and resends email

### Faculty Workflow

1. **Receive Welcome Email**
   - Faculty receives email with:
     - Temporary password
     - Login link
     - Instructions

2. **First Login**
   - Go to `http://localhost:5173/login`
   - Enter email and temporary password
   - System automatically redirects to first-time setup

3. **Complete Setup**
   - **Step 1: Set Password**
     - Enter temporary password
     - Create new password (must meet requirements)
     - Confirm new password
     - Password requirements:
       - At least 8 characters
       - One uppercase letter
       - One lowercase letter
       - One number
       - One special character
   
   - **Step 2: Complete Profile (Optional)**
     - Add phone number
     - Add bio
   
   - Click "Complete Setup"

4. **Access Portal**
   - After setup, faculty can login with their new password
   - Access dashboard and other features

## API Endpoints

### Authentication
- `POST /api/v1/auth/login` - Login with email and password
- `POST /api/v1/auth/first-time-setup` - Complete first-time setup
- `POST /api/v1/auth/change-password` - Change password
- `GET /api/v1/auth/me` - Get current user info
- `POST /api/v1/auth/verify-token` - Verify JWT token

### Faculty Management (Admin Only)
- `POST /api/v1/faculty` - Create new faculty
- `GET /api/v1/faculty` - List all faculty
- `GET /api/v1/faculty/pending-setup` - List faculty pending setup
- `GET /api/v1/faculty/{id}` - Get faculty details
- `PUT /api/v1/faculty/{id}` - Update faculty
- `DELETE /api/v1/faculty/{id}` - Delete faculty
- `POST /api/v1/faculty/{id}/resend-credentials` - Resend credentials

## Security Features

1. **Password Hashing**: All passwords are hashed using bcrypt
2. **JWT Tokens**: Secure token-based authentication
3. **Temporary Password Expiry**: Temp passwords expire after 7 days
4. **Failed Login Tracking**: Account locks after 5 failed attempts
5. **Password Strength Validation**: Enforces strong password requirements
6. **Role-Based Access**: Admin-only routes protected
7. **First-Login Detection**: Automatic redirect to setup page

## Troubleshooting

### Backend Issues

**MongoDB Connection Error**
```
Solution: Ensure MongoDB is running on localhost:27017
```

**Import Errors**
```
Solution: Install all dependencies: pip install -r requirements.txt
```

**Port Already in Use**
```
Solution: Change port in uvicorn command: uvicorn main:app --port 8001
```

### Frontend Issues

**API Connection Error**
```
Solution: Ensure backend is running on port 8000
Check CORS settings in backend/app/main.py
```

**Module Not Found**
```
Solution: Run npm install to install all dependencies
```

### Email Issues

**Emails Not Sending (Mock Mode)**
```
Solution: Check console output - emails are logged there
```

**SMTP Authentication Failed**
```
Solution: 
- For Gmail, use App Password, not regular password
- Enable 2-factor authentication first
- Check SMTP settings in config.py
```

## Testing

### Manual Testing Checklist

- [ ] Admin can login
- [ ] Admin can create faculty
- [ ] Welcome email is sent/logged
- [ ] Faculty can login with temp password
- [ ] Faculty is redirected to setup page
- [ ] Password validation works
- [ ] Faculty can complete setup
- [ ] Faculty can login with new password
- [ ] Temp password expires after 7 days
- [ ] Account locks after failed attempts
- [ ] Resend credentials works

### API Testing with cURL

**Create Faculty:**
```bash
curl -X POST http://localhost:8000/api/v1/faculty \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "name": "John Doe",
    "email": "john.doe@bmsit.in",
    "department": "Computer Science (CSE)",
    "designation": "Assistant Professor"
  }'
```

**Login:**
```bash
curl -X POST http://localhost:8000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@bmsit.in",
    "password": "Admin@123"
  }'
```

## Production Deployment

Before deploying to production:

1. **Change Default Credentials**
   - Change admin password
   - Update JWT secret key

2. **Enable Real Email**
   - Set `USE_MOCK_EMAIL = False`
   - Configure SMTP settings

3. **Secure MongoDB**
   - Use authentication
   - Use remote MongoDB instance
   - Enable SSL/TLS

4. **Environment Variables**
   - Move sensitive config to environment variables
   - Use `.env` file (not committed to git)

5. **HTTPS**
   - Use HTTPS in production
   - Update CORS origins

## Support

For issues or questions:
- Check the troubleshooting section
- Review API documentation
- Check console logs for errors
- Verify MongoDB is running
- Ensure all dependencies are installed

## Features Summary

✅ Admin creates faculty profiles  
✅ Automatic temporary password generation  
✅ Email notifications (mock and real SMTP)  
✅ First-time login detection  
✅ Secure password change flow  
✅ Password strength validation  
✅ Profile completion  
✅ JWT authentication  
✅ Role-based access control  
✅ Account security (lockout, expiry)  
✅ Resend credentials functionality  
✅ Pending setup tracking  

## Next Steps

Consider adding:
- Password reset flow (forgot password)
- Email verification
- Profile picture upload
- Bulk faculty import (CSV)
- Activity logging
- Admin dashboard with statistics
- Email templates customization
