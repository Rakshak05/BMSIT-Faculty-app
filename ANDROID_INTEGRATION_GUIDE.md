# Android App Integration Guide - Faculty Onboarding

## Overview

This guide shows how to integrate the faculty onboarding backend API with your Android app. Everything happens in the app - no website needed.

---

## Backend API Setup

### 1. Start the Backend Server

```powershell
# Install dependencies
cd backend
pip install -r requirements.txt

# Create admin user (one-time)
python create_admin.py

# Start server
cd app
uvicorn main:app --reload --port 8000
```

Backend will run at: `http://localhost:8000`

### 2. API Base URL

In your Android app, set:
```kotlin
const val BASE_URL = "http://10.0.2.2:8000/api/v1/"  // For Android emulator
// or
const val BASE_URL = "http://YOUR_COMPUTER_IP:8000/api/v1/"  // For physical device
```

---

## API Endpoints for Android

### Authentication Endpoints

#### 1. Login (Handles Both Temp & Regular Passwords)

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "user@bmsit.in",
  "password": "password123"
}
```

**Response:**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "bearer",
  "user": {
    "_id": "507f1f77bcf86cd799439011",
    "name": "Dr. John Doe",
    "email": "john.doe@bmsit.in",
    "department": "Computer Science (CSE)",
    "designation": "Assistant Professor",
    "role": "faculty",
    "is_first_login": true,  // ⚠️ Check this!
    "password_change_required": true,
    "email_verified": false,
    "created_at": "2025-11-25T06:00:00Z"
  }
}
```

**Android Implementation:**
```kotlin
data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val access_token: String,
    val token_type: String,
    val user: User
)

data class User(
    @SerializedName("_id") val id: String,
    val name: String,
    val email: String,
    val department: String,
    val designation: String,
    val role: String,
    val is_first_login: Boolean,
    val password_change_required: Boolean,
    val email_verified: Boolean,
    val phone: String?,
    val bio: String?,
    val created_at: String
)

// Retrofit interface
interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}

// Usage
val response = authApi.login(LoginRequest(email, password))
if (response.user.is_first_login) {
    // Navigate to First-Time Setup screen
    navigateToFirstTimeSetup()
} else {
    // Navigate to main dashboard
    navigateToDashboard()
}
```

---

#### 2. First-Time Setup (Change Password)

```http
POST /api/v1/auth/first-time-setup
Authorization: Bearer {token}
Content-Type: application/json

{
  "temp_password": "TempPass123!",
  "new_password": "MyNewSecure@Pass123",
  "phone": "+91 9876543210",  // Optional
  "bio": "Passionate about teaching..."  // Optional
}
```

**Response:**
```json
{
  "_id": "507f1f77bcf86cd799439011",
  "name": "Dr. John Doe",
  "email": "john.doe@bmsit.in",
  "is_first_login": false,  // Now false!
  "password_change_required": false,
  ...
}
```

**Android Implementation:**
```kotlin
data class FirstTimeSetupRequest(
    val temp_password: String,
    val new_password: String,
    val phone: String? = null,
    val bio: String? = null
)

interface AuthApi {
    @POST("auth/first-time-setup")
    suspend fun firstTimeSetup(
        @Header("Authorization") token: String,
        @Body request: FirstTimeSetupRequest
    ): User
}

// Usage
val token = "Bearer ${savedToken}"
val response = authApi.firstTimeSetup(
    token,
    FirstTimeSetupRequest(
        temp_password = tempPassword,
        new_password = newPassword,
        phone = phoneNumber,
        bio = bio
    )
)
// Navigate to dashboard
navigateToDashboard()
```

---

#### 3. Change Password (After First Login)

```http
POST /api/v1/auth/change-password
Authorization: Bearer {token}
Content-Type: application/json

{
  "old_password": "OldPass123!",
  "new_password": "NewSecure@Pass456"
}
```

---

#### 4. Get Current User

```http
GET /api/v1/auth/me
Authorization: Bearer {token}
```

---

### Faculty Management Endpoints (Admin Only)

#### 1. Create Faculty (Admin)

```http
POST /api/v1/faculty
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "name": "Dr. Jane Smith",
  "email": "jane.smith@bmsit.in",
  "department": "Computer Science (CSE)",
  "designation": "Assistant Professor",
  "employee_id": "FAC001",  // Optional
  "phone": "+91 9876543210",  // Optional
  "role": "faculty"
}
```

**Response:**
```json
{
  "_id": "507f1f77bcf86cd799439012",
  "name": "Dr. Jane Smith",
  "email": "jane.smith@bmsit.in",
  "is_first_login": true,
  ...
}
```

**Note:** Backend automatically:
- Generates temporary password
- Sends welcome email (or logs to console in dev mode)
- Sets `is_first_login: true`

---

#### 2. List All Faculty (Admin)

```http
GET /api/v1/faculty?department=CSE&skip=0&limit=50
Authorization: Bearer {admin_token}
```

---

#### 3. Get Pending Setups (Admin)

```http
GET /api/v1/faculty/pending-setup
Authorization: Bearer {admin_token}
```

Returns faculty who haven't completed first-time setup.

---

#### 4. Resend Credentials (Admin)

```http
POST /api/v1/faculty/{faculty_id}/resend-credentials
Authorization: Bearer {admin_token}
```

Generates new temp password and sends email.

---

## Android App Screens Needed

### 1. Login Screen

**Fields:**
- Email input
- Password input
- Login button

**Logic:**
```kotlin
fun onLoginClick() {
    viewModelScope.launch {
        try {
            val response = authApi.login(LoginRequest(email, password))
            
            // Save token
            tokenManager.saveToken(response.access_token)
            
            // Check if first-time login
            if (response.user.is_first_login) {
                navigateToFirstTimeSetup()
            } else {
                navigateToDashboard()
            }
        } catch (e: Exception) {
            showError("Invalid credentials")
        }
    }
}
```

---

### 2. First-Time Setup Screen

**Step 1: Change Password**
- Temporary password input
- New password input
- Confirm password input
- Password strength indicator
- Requirements checklist

**Step 2: Complete Profile (Optional)**
- Phone number input
- Bio text area

**Logic:**
```kotlin
fun onCompleteSetup() {
    // Validate password
    if (!isPasswordValid(newPassword)) {
        showError("Password doesn't meet requirements")
        return
    }
    
    if (newPassword != confirmPassword) {
        showError("Passwords don't match")
        return
    }
    
    viewModelScope.launch {
        try {
            val token = "Bearer ${tokenManager.getToken()}"
            val response = authApi.firstTimeSetup(
                token,
                FirstTimeSetupRequest(
                    temp_password = tempPassword,
                    new_password = newPassword,
                    phone = phone,
                    bio = bio
                )
            )
            
            showSuccess("Setup complete!")
            navigateToDashboard()
        } catch (e: Exception) {
            showError("Setup failed: ${e.message}")
        }
    }
}

fun isPasswordValid(password: String): Boolean {
    return password.length >= 8 &&
           password.any { it.isUpperCase() } &&
           password.any { it.isLowerCase() } &&
           password.any { it.isDigit() } &&
           password.any { !it.isLetterOrDigit() }
}
```

---

### 3. Add Faculty Screen (Admin Only)

**Fields:**
- Name input
- Email input
- Department dropdown
- Designation dropdown
- Employee ID input (optional)
- Phone input (optional)
- Submit button

**Logic:**
```kotlin
fun onAddFaculty() {
    viewModelScope.launch {
        try {
            val token = "Bearer ${tokenManager.getToken()}"
            val response = facultyApi.createFaculty(
                token,
                FacultyCreateRequest(
                    name = name,
                    email = email,
                    department = department,
                    designation = designation,
                    employee_id = employeeId,
                    phone = phone,
                    role = "faculty"
                )
            )
            
            showSuccess("Faculty added! Email sent with temp password.")
            navigateBack()
        } catch (e: Exception) {
            showError("Failed to add faculty: ${e.message}")
        }
    }
}
```

---

## Password Validation (Client-Side)

```kotlin
data class PasswordStrength(
    val score: Int,  // 0-100
    val label: String,  // Weak, Medium, Strong
    val color: Color
)

fun calculatePasswordStrength(password: String): PasswordStrength {
    var score = 0
    
    if (password.length >= 8) score += 20
    if (password.length >= 12) score += 10
    if (password.any { it.isLowerCase() }) score += 20
    if (password.any { it.isUpperCase() }) score += 20
    if (password.any { it.isDigit() }) score += 15
    if (password.any { !it.isLetterOrDigit() }) score += 15
    
    return when {
        score < 40 -> PasswordStrength(score, "Weak", Color.Red)
        score < 70 -> PasswordStrength(score, "Medium", Color.Yellow)
        else -> PasswordStrength(score, "Strong", Color.Green)
    }
}

// Password requirements checklist
data class PasswordRequirement(
    val label: String,
    val isMet: (String) -> Boolean
)

val passwordRequirements = listOf(
    PasswordRequirement("At least 8 characters") { it.length >= 8 },
    PasswordRequirement("One uppercase letter") { it.any { c -> c.isUpperCase() } },
    PasswordRequirement("One lowercase letter") { it.any { c -> c.isLowerCase() } },
    PasswordRequirement("One number") { it.any { c -> c.isDigit() } },
    PasswordRequirement("One special character") { it.any { c -> !c.isLetterOrDigit() } }
)
```

---

## Retrofit Setup

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
}

// ApiClient.kt
object ApiClient {
    private const val BASE_URL = "http://10.0.2.2:8000/api/v1/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()
    
    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val facultyApi: FacultyApi = retrofit.create(FacultyApi::class.java)
}
```

---

## Complete User Flow

### Admin Creates Faculty

1. Admin logs in to Android app
2. Admin navigates to "Add Faculty" screen
3. Admin fills form and submits
4. Backend creates user with temp password
5. Backend sends email to faculty (check backend console for temp password in dev mode)
6. Success message shown to admin

### Faculty First-Time Login

1. Faculty receives email with temp password
2. Faculty opens Android app
3. Faculty enters email + temp password
4. App detects `is_first_login: true`
5. App navigates to First-Time Setup screen
6. Faculty enters temp password, creates new password
7. Faculty optionally adds phone/bio
8. Faculty submits
9. Backend validates and updates user
10. App navigates to dashboard

### Faculty Regular Login

1. Faculty opens Android app
2. Faculty enters email + new password
3. App detects `is_first_login: false`
4. App navigates directly to dashboard

---

## Testing

### 1. Start Backend

```powershell
cd backend
python create_admin.py  # One-time
cd app
uvicorn main:app --reload --port 8000
```

### 2. Test with Postman/cURL

```bash
# Login as admin
curl -X POST http://localhost:8000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@bmsit.in","password":"Admin@123"}'

# Create faculty
curl -X POST http://localhost:8000/api/v1/faculty \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "name":"Test Faculty",
    "email":"test@bmsit.in",
    "department":"Computer Science (CSE)",
    "designation":"Assistant Professor",
    "role":"faculty"
  }'
```

### 3. Check Backend Console

Look for the temp password in the email log:
```
📧 MOCK EMAIL SERVICE
To: test@bmsit.in
Temporary Password: Xy9#mK2pL5qR
```

### 4. Test in Android App

- Login with temp password
- Complete first-time setup
- Login with new password

---

## Security Notes

1. **HTTPS in Production**: Use HTTPS, not HTTP
2. **Token Storage**: Store JWT token securely (EncryptedSharedPreferences)
3. **Token Expiry**: Handle 401 errors and refresh tokens
4. **Password Validation**: Validate on both client and server
5. **Network Security Config**: Add your backend domain to network security config

---

## Next Steps

1. ✅ Backend is ready (FastAPI)
2. 📱 Implement Android screens (Login, First-Time Setup, Add Faculty)
3. 🔌 Integrate Retrofit API calls
4. 🧪 Test the complete flow
5. 🚀 Deploy backend to production server

---

**The backend API is complete and ready for your Android app to use!**
