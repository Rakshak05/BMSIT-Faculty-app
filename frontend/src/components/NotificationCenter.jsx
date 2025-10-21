import React, { useState, useEffect } from 'react'
import { notificationService } from '../services/notificationService'
import './NotificationCenter.css'

const NotificationCenter = ({ recipientId }) => {
  const [notifications, setNotifications] = useState([])
  const [isOpen, setIsOpen] = useState(false)
  const [loading, setLoading] = useState(false)

  // Fetch notifications
  const fetchNotifications = async () => {
    if (!recipientId) return
    
    try {
      setLoading(true)
      const data = await notificationService.getNotifications(recipientId)
      setNotifications(data)
    } catch (error) {
      console.error('Failed to fetch notifications:', error)
    } finally {
      setLoading(false)
    }
  }

  // Fetch notifications on component mount and when recipientId changes
  useEffect(() => {
    fetchNotifications()
  }, [recipientId])

  // Refresh notifications every 30 seconds (real-time updates)
  useEffect(() => {
    const interval = setInterval(() => {
      fetchNotifications()
    }, 30000) // 30 seconds

    return () => clearInterval(interval)
  }, [recipientId])

  // Mark a notification as read
  const handleMarkAsRead = async (notificationId) => {
    try {
      await notificationService.markAsRead(notificationId)
      setNotifications(notifications.map(n => 
        n.id === notificationId ? { ...n, read: true } : n
      ))
    } catch (error) {
      console.error('Failed to mark as read:', error)
    }
  }

  // Mark all notifications as read
  const handleMarkAllAsRead = async () => {
    try {
      await notificationService.markAllAsRead(recipientId)
      setNotifications(notifications.map(n => ({ ...n, read: true })))
    } catch (error) {
      console.error('Failed to mark all as read:', error)
    }
  }

  // Delete a notification
  const handleDelete = async (notificationId) => {
    try {
      await notificationService.deleteNotification(notificationId)
      setNotifications(notifications.filter(n => n.id !== notificationId))
    } catch (error) {
      console.error('Failed to delete notification:', error)
    }
  }

  // Clear all notifications
  const handleClearAll = async () => {
    try {
      await notificationService.clearAllNotifications(recipientId)
      setNotifications([])
    } catch (error) {
      console.error('Failed to clear notifications:', error)
    }
  }

  // Format date for display
  const formatDate = (dateString) => {
    const date = new Date(dateString)
    return date.toLocaleString()
  }

  return (
    <div className="notification-center">
      <div className="notification-header">
        <h2>Notifications</h2>
        <div className="notification-actions">
          <button onClick={handleMarkAllAsRead} disabled={loading || notifications.length === 0}>
            Mark All as Read
          </button>
          <button onClick={handleClearAll} disabled={loading || notifications.length === 0}>
            Clear All
          </button>
        </div>
      </div>
      
      {loading ? (
        <p>Loading notifications...</p>
      ) : notifications.length === 0 ? (
        <p>No notifications found.</p>
      ) : (
        <div className="notification-list">
          {notifications.map(notification => (
            <div 
              key={notification.id} 
              className={`notification-item ${notification.read ? 'read' : 'unread'}`}
            >
              <div className="notification-content">
                <h3 className="notification-title">{notification.title}</h3>
                <p className="notification-message">{notification.message}</p>
                <div className="notification-meta">
                  <span className="notification-time">{formatDate(notification.created_at)}</span>
                  <span className={`notification-type ${notification.type}`}>
                    {notification.type}
                  </span>
                </div>
              </div>
              <div className="notification-actions">
                {!notification.read && (
                  <button 
                    onClick={() => handleMarkAsRead(notification.id)}
                    className="mark-read-btn"
                  >
                    Mark as Read
                  </button>
                )}
                <button 
                  onClick={() => handleDelete(notification.id)}
                  className="delete-btn"
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default NotificationCenter