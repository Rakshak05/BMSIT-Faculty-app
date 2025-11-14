import { useState, useEffect } from 'react'
import { Toaster } from 'sonner'
import CalendarDashboard from './components/calendar/CalendarDashboard'
import './App.css'

function App() {
  return (
    <div className="App">
      <Toaster position="top-right" />
      <CalendarDashboard />
    </div>
  )
}

export default App
