# 🚀 Quick Start - Step by Step

## Current Status
✅ All code implemented (21 files)  
✅ Dependencies listed in requirements.txt  
✅ Documentation complete  
⚠️ MongoDB needs to be running  

---

## Option 1: I Already Have MongoDB Running

If MongoDB is already installed and running, skip to **Step 2** below.

---

## Option 2: Install MongoDB (First Time)

### Download & Install MongoDB

1. **Download MongoDB Community Server**
   - Visit: https://www.mongodb.com/try/download/community
   - Select: Windows
   - Version: Latest (7.0 or higher)
   - Package: MSI

2. **Install MongoDB**
   - Run the downloaded MSI file
   - Choose "Complete" installation
   - ✅ Check "Install MongoDB as a Service"
   - ✅ Check "Run service as Network Service user"
   - Click "Install"

3. **Verify Installation**
   ```powershell
   # Open new PowerShell window
   mongod --version
   ```

   If this works, MongoDB is installed! ✅

### Alternative: MongoDB Atlas (Cloud - No Installation)

If you prefer not to install MongoDB locally:

1. **Create Free Account**
   - Visit: https://www.mongodb.com/cloud/atlas/register
   - Sign up for free tier

2. **Create Cluster**
   - Choose free tier (M0)
   - Select region closest to you
   - Click "Create"

3. **Get Connection String**
   - Click "Connect" → "Connect your application"
   - Copy connection string
   - Update `backend/app/config.py`:
     ```python
     MONGODB_URL: str = "your-connection-string-here"
     ```

---

## Step-by-Step Startup Guide

### Step 1: Ensure MongoDB is Running

**If installed locally:**
```powershell
# Check if MongoDB service is running
Get-Service MongoDB

# If not running, start it:
Start-Service MongoDB
```

**If using Atlas:**
- Just ensure you've updated the connection string in `config.py`

---

### Step 2: Create Admin User

```powershell
# Navigate to backend directory
cd backend

# Run admin creation script
python create_admin.py
```

**Expected Output:**
```
============================================================
BMSIT Faculty Portal - Admin User Setup
============================================================

Creating default admin user...
✅ Connected to MongoDB
✅ Admin user created successfully!
   Email: admin@bmsit.in
   Password: Admin@123

⚠️  IMPORTANT: Please change the admin password after first login!
```

**If you see an error:**
- "MongoDB connection error" → MongoDB isn't running
- "Admin already exists" → Already created, skip to next step

---

### Step 3: Start Backend Server

```powershell
# From backend directory
cd app

# Start the server
uvicorn main:app --reload --port 8000
```

**Expected Output:**
```
INFO:     Uvicorn running on http://127.0.0.1:8000
INFO:     Application startup complete.
Connected to MongoDB
```

**Keep this terminal open!** The backend is now running.

---

### Step 4: Start Frontend Server

**Open a NEW terminal/PowerShell window:**

```powershell
# Navigate to frontend directory
cd d:\BMSIT-Faculty-app\frontend

# Install dependencies (first time only)
npm install

# Start development server
npm run dev
```

**Expected Output:**
```
  VITE v4.4.5  ready in 500 ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
```

**Keep this terminal open too!** The frontend is now running.

---

### Step 5: Test the System

1. **Open Browser**
   - Navigate to: http://localhost:5173/login

2. **Login as Admin**
   - Email: `admin@bmsit.in`
   - Password: `Admin@123`
   - Click "Sign In"

3. **Create Test Faculty**
   - Navigate to "Add Faculty" (or go to `/add-faculty`)
   - Fill in the form:
     - Name: `Test Faculty`
     - Email: `test.faculty@bmsit.in`
     - Department: `Computer Science (CSE)`
     - Designation: `Assistant Professor`
   - Click "Add Member"

4. **Check Console for Email**
   - Look at the **backend terminal**
   - You should see the welcome email with temporary password
   - Copy the temporary password (e.g., `Xy9#mK2pL5qR`)

5. **Test Faculty Login**
   - Logout from admin account
   - Login with:
     - Email: `test.faculty@bmsit.in`
     - Password: (the temp password from console)
   - You should be redirected to first-time setup

6. **Complete Setup**
   - Enter temporary password
   - Create new password (must meet requirements)
   - Optionally add phone/bio
   - Click "Complete Setup"

7. **Verify Regular Login**
   - Logout
   - Login again with new password
   - Should go directly to dashboard (no setup page)

---

## Troubleshooting

### MongoDB Connection Issues

**Error:** `ServerSelectionTimeoutError`
```
Solution:
1. Check if MongoDB service is running:
   Get-Service MongoDB
2. If stopped, start it:
   Start-Service MongoDB
3. Restart backend server
```

**Error:** `mongod: command not found`
```
Solution:
1. MongoDB not installed or not in PATH
2. Install MongoDB Community Server
3. Or use MongoDB Atlas (cloud)
```

### Backend Issues

**Error:** `ModuleNotFoundError`
```
Solution:
cd backend
pip install -r requirements.txt
```

**Error:** `Port 8000 already in use`
```
Solution:
# Use different port
uvicorn main:app --reload --port 8001

# Update frontend config to use new port
# Edit: frontend/src/config/config.js
# Change: http://localhost:8001/api/v1
```

### Frontend Issues

**Error:** `npm: command not found`
```
Solution:
1. Install Node.js from https://nodejs.org/
2. Restart terminal
3. Try again
```

**Error:** `Port 5173 already in use`
```
Solution:
Vite will automatically use next available port (5174, 5175, etc.)
Just use the port shown in terminal output
```

### Email Not Showing

**Issue:** Can't see welcome email
```
Solution:
1. Check backend terminal output
2. Email is logged to console in dev mode
3. Look for "📧 MOCK EMAIL SERVICE" section
4. Copy the temporary password from there
```

---

## Quick Commands Reference

### Start Everything (After First Setup)

**Terminal 1 - Backend:**
```powershell
cd d:\BMSIT-Faculty-app\backend\app
uvicorn main:app --reload --port 8000
```

**Terminal 2 - Frontend:**
```powershell
cd d:\BMSIT-Faculty-app\frontend
npm run dev
```

### Stop Servers

- Press `Ctrl+C` in each terminal window

### Reset Admin Password

```powershell
cd d:\BMSIT-Faculty-app\backend
# Delete existing admin from MongoDB
# Then run:
python create_admin.py
```

---

## Next Steps After Testing

1. ✅ **Change Admin Password**
   - Login as admin
   - Use change password feature

2. ✅ **Configure Email (Optional)**
   - Edit `backend/app/config.py`
   - Set `USE_MOCK_EMAIL = False`
   - Add SMTP credentials

3. ✅ **Update JWT Secret**
   - Edit `backend/app/config.py`
   - Change `SECRET_KEY` to a secure random string

4. ✅ **Deploy to Production**
   - Set up production MongoDB
   - Configure environment variables
   - Enable HTTPS

---

## 📞 Need Help?

Check these resources:
1. [Visual User Guide](file:///C:/Users/RAKSHAK/.gemini/antigravity/brain/8ef90d5b-a538-4335-8076-4bc688a99a89/visual_user_guide.md) - UI mockups and workflow
2. [README.md](file:///d:/BMSIT-Faculty-app/README.md) - Project overview
3. [Setup Guide](file:///d:/BMSIT-Faculty-app/FACULTY_ONBOARDING_GUIDE.md) - Detailed configuration

---

**🎊 You're all set! Follow the steps above to start testing your faculty onboarding system.**
