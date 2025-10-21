import React, { useState, useEffect } from 'react'
import { notificationService } from '../services/notificationService'
import './NotificationBell.css'

const NotificationBell = ({ recipientId }) => {
  const [unreadCount, setUnreadCount] = useState(0)
  const [isLoading, setIsLoading] = useState(false)

  // Fetch unread count
  const fetchUnreadCount = async () => {
    if (!recipientId) return
    
    try {
      setIsLoading(true)
      const count = await notificationService.getUnreadCount(recipientId)
      setUnreadCount(count)
    } catch (error) {
      console.error('Failed to fetch unread count:', error)
    } finally {
      setIsLoading(false)
    }
  }

  // Fetch unread count on component mount and when recipientId changes
  useEffect(() => {
    fetchUnreadCount()
  }, [recipientId])

  // Refresh unread count every 30 seconds (real-time updates)
  useEffect(() => {
    const interval = setInterval(() => {
      fetchUnreadCount()
    }, 30000) // 30 seconds

    return () => clearInterval(interval)
  }, [recipientId])

  return (
    <div className="notification-bell" onClick={() => {}}>
      <svg 
        xmlns="http://www.w3.org/2000/svg" 
        width="24" 
        height="24" 
        viewBox="0 0 24 24" 
        fill="none" 
        stroke="currentColor" 
        strokeWidth="2" 
        strokeLinecap="round" 
        strokeLinejoin="round"
        style={{ cursor: 'pointer' }}
      >
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
      {unreadCount > 0 && (
        <span className="notification-badge">
          {unreadCount > 99 ? '99+' : unreadCount}
        </span>
      )}
    </div>
  )
}

export default NotificationBell