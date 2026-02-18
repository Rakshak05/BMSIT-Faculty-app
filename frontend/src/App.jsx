import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { Toaster } from 'sonner'
import { AuthProvider } from './context/AuthContext'
import ProtectedRoute from './components/ProtectedRoute'
import CalendarDashboard from './components/calendar/CalendarDashboard'
import AddFacultyScreen from './pages/AddFacultyScreen'
import FacultyMembersPage from './pages/FacultyMembersPage'
import ProfilePage from './pages/ProfilePage'
import LoginPage from './pages/LoginPage'
import FirstTimeSetup from './pages/FirstTimeSetup'
import './App.css'

function App() {
  return (
    <Router>
      <AuthProvider>
        <div className="App">
          <Toaster position="top-right" richColors />
          <Routes>
            {/* Public Routes */}
            <Route path="/login" element={<LoginPage />} />

            {/* First-Time Setup Route (requires authentication but not full setup) */}
            <Route path="/first-time-setup" element={<FirstTimeSetup />} />

            {/* Protected Routes */}
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <CalendarDashboard />
                </ProtectedRoute>
              }
            />
            <Route
              path="/faculty-members"
              element={
                <ProtectedRoute>
                  <FacultyMembersPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/profile"
              element={
                <ProtectedRoute>
                  <ProfilePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/add-faculty"
              element={
                <ProtectedRoute requireAdmin={true}>
                  <AddFacultyScreen />
                </ProtectedRoute>
              }
            />

            {/* Catch all - redirect to login */}
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </div>
      </AuthProvider>
    </Router>
  )
}

export default App