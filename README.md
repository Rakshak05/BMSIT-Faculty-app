# BMSIT Faculty Portal - Onboarding System

A comprehensive faculty management system with secure admin-managed onboarding workflow.

## 🚀 Quick Start

### Prerequisites
- **Python 3.8+** - [Download](https://www.python.org/downloads/)
- **Node.js 16+** - [Download](https://nodejs.org/)
- **MongoDB** - [Download](https://www.mongodb.com/try/download/community)

### Option 1: Automated Setup (Windows)

1. **Ensure MongoDB is running**
2. **Run the quick start script:**
   ```bash
   quick-start.bat
   ```
3. **Login at** `http://localhost:5173/login`
   - Email: `admin@bmsit.in`
   - Password: `Admin@123`

### Option 2: Manual Setup

#### Backend Setup

```bash
# Navigate to backend
cd backend

# Install dependencies
pip install -r requirements.txt

# Create admin user
python create_admin.py

# Start backend server
cd app
uvicorn main:app --reload --port 8000
```

Backend will be available at `http://localhost:8000`

#### Frontend Setup

```bash
# Navigate to frontend (in a new terminal)
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev
```

Frontend will be available at `http://localhost:5173`

## 📚 Documentation

- **[Setup Guide](FACULTY_ONBOARDING_GUIDE.md)** - Comprehensive setup and configuration guide
- **[API Documentation](http://localhost:8000/docs)** - Interactive API docs (when backend is running)

## ✨ Features

### Admin Features
- ✅ Create faculty profiles with automatic password generation
- ✅ Send welcome emails with temporary credentials
- ✅ Track pending first-time setups
- ✅ Resend credentials to faculty
- ✅ Full faculty management (CRUD operations)

### Faculty Features
- ✅ Secure first-time login with temporary password
- ✅ Guided password change with strength validation
- ✅ Profile completion
- ✅ Secure JWT-based authentication

### Security Features
- ✅ Password hashing with bcrypt
- ✅ JWT token authentication
- ✅ Temporary password expiry (7 days)
- ✅ Account lockout after failed attempts
- ✅ Strong password requirements
- ✅ Role-based access control

## 🔐 Default Credentials

**Admin Account:**
- Email: `admin@bmsit.in`
- Password: `Admin@123`

⚠️ **IMPORTANT:** Change this password immediately after first login!

## 📖 Usage Workflow

### Admin: Creating a New Faculty Member

1. Login as admin
2. Navigate to "Add Faculty"
3. Fill in faculty details:
   - Full Name (required)
   - Email (required, must end with @bmsit.in)
   - Department (required)
   - Designation (required)
   - Employee ID (optional)
   - Phone (optional)
4. Click "Add Member"
5. System automatically:
   - Generates secure temporary password
   - Sends welcome email (or logs to console in dev mode)

### Faculty: First-Time Setup

1. Receive welcome email with temporary password
2. Visit login page: `http://localhost:5173/login`
3. Login with email and temporary password
4. Complete two-step setup:
   - **Step 1:** Change password (with strength validation)
   - **Step 2:** Complete profile (optional)
5. Access the portal with new credentials

## 🛠️ Configuration

### Email Service

By default, the system uses **mock email mode** (logs to console).

To enable real emails, edit `backend/app/config.py`:

```python
USE_MOCK_EMAIL: bool = False
SMTP_HOST: str = "smtp.gmail.com"
SMTP_PORT: int = 587
SMTP_USERNAME: str = "your-email@gmail.com"
SMTP_PASSWORD: str = "your-app-password"
```

### MongoDB Connection

Edit `backend/app/config.py`:

```python
MONGODB_URL: str = "mongodb://localhost:27017"
MONGODB_DATABASE: str = "bmsit_faculty_db"
```

### JWT Secret Key

For production, change in `backend/app/config.py`:

```python
SECRET_KEY: str = "your-secure-secret-key-min-32-chars"
```

## 🧪 Testing

### Manual Testing Checklist

- [ ] Admin can login
- [ ] Admin can create faculty
- [ ] Welcome email is sent/logged
- [ ] Faculty can login with temp password
- [ ] Faculty completes first-time setup
- [ ] Password validation works
- [ ] Faculty can login with new password

### API Testing

Access interactive API docs at `http://localhost:8000/docs` when backend is running.

## 📁 Project Structure

```
BMSIT-Faculty-app/
├── backend/
│   ├── app/
│   │   ├── models/          # Data models (user.py)
│   │   ├── routes/          # API routes (auth.py, faculty.py)
│   │   ├── services/        # Email service
│   │   ├── utils/           # Auth utilities
│   │   ├── database/        # MongoDB connection
│   │   ├── config.py        # Configuration
│   │   └── main.py          # FastAPI app
│   ├── create_admin.py      # Admin setup script
│   └── requirements.txt     # Python dependencies
├── frontend/
│   ├── src/
│   │   ├── components/      # React components
│   │   ├── pages/           # Page components
│   │   ├── services/        # API services
│   │   ├── context/         # Auth context
│   │   └── config/          # Frontend config
│   └── package.json         # Node dependencies
├── quick-start.bat          # Quick setup script
├── FACULTY_ONBOARDING_GUIDE.md  # Detailed guide
└── README.md                # This file
```

## 🔧 Troubleshooting

### MongoDB Connection Error
```
Solution: Ensure MongoDB is running
- Windows: Start MongoDB service
- Or run: mongod
```

### Port Already in Use
```
Backend: Change port in uvicorn command
Frontend: Port will auto-increment if 5173 is busy
```

### Email Not Sending
```
In development: Check console - emails are logged there
In production: Verify SMTP settings in config.py
```

## 🚀 Production Deployment

Before deploying:

1. ✅ Change admin password
2. ✅ Update JWT secret key
3. ✅ Configure real email service
4. ✅ Use secure MongoDB connection
5. ✅ Enable HTTPS
6. ✅ Update CORS origins

## 📝 API Endpoints

### Authentication
- `POST /api/v1/auth/login` - Login
- `POST /api/v1/auth/first-time-setup` - Complete setup
- `POST /api/v1/auth/change-password` - Change password
- `GET /api/v1/auth/me` - Get current user

### Faculty Management (Admin Only)
- `POST /api/v1/faculty` - Create faculty
- `GET /api/v1/faculty` - List faculty
- `GET /api/v1/faculty/pending-setup` - Pending setups
- `PUT /api/v1/faculty/{id}` - Update faculty
- `DELETE /api/v1/faculty/{id}` - Delete faculty
- `POST /api/v1/faculty/{id}/resend-credentials` - Resend email

## 🤝 Support

For issues or questions:
1. Check the [Setup Guide](FACULTY_ONBOARDING_GUIDE.md)
2. Review console logs for errors
3. Verify MongoDB is running
4. Ensure all dependencies are installed

## 📄 License

This project is part of the BMSIT Faculty Management System.

---

**Made with ❤️ for BMSIT**
