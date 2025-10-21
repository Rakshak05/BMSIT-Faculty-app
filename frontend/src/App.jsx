import { useState, useEffect } from 'react'
import { Toaster, toast } from 'sonner'
import './App.css'
import NotificationBell from './components/NotificationBell'
import NotificationCenter from './components/NotificationCenter'
import './components/NotificationBell.css'

function App() {
  const [recipientId, setRecipientId] = useState('user123') // Default user ID for testing

  // Function to show toast notifications
  const showToast = (type, message) => {
    switch (type) {
      case 'success':
        toast.success(message)
        break
      case 'error':
        toast.error(message)
        break
      case 'warning':
        toast.warning(message)
        break
      default:
        toast(message)
    }
  }

  return (
    <div className="App">
      <Toaster position="top-right" />
      <header>
        <h1>Notification System</h1>
        <div className="header-right">
          <NotificationBell recipientId={recipientId} />
        </div>
      </header>
      <main>
        <div className="controls">
          <input 
            type="text" 
            value={recipientId} 
            onChange={(e) => setRecipientId(e.target.value)} 
            placeholder="Recipient ID"
          />
          <button onClick={() => showToast('success', 'This is a success message!')}>
            Show Success Toast
          </button>
          <button onClick={() => showToast('error', 'This is an error message!')}>
            Show Error Toast
          </button>
          <button onClick={() => showToast('warning', 'This is a warning message!')}>
            Show Warning Toast
          </button>
          <button onClick={() => showToast('info', 'This is an info message!')}>
            Show Info Toast
          </button>
        </div>
        <NotificationCenter recipientId={recipientId} />
      </main>
    </div>
  )
}

export default App