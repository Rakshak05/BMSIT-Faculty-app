import axios from 'axios'

const API_BASE_URL = 'http://localhost:8000/api/v1'

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
})

export const notificationService = {
  // Get all notifications for a recipient
  getNotifications: async (recipientId, read = null) => {
    try {
      const params = { recipient_id: recipientId }
      if (read !== null) params.read = read
      const response = await api.get('/notifications', { params })
      return response.data
    } catch (error) {
      throw new Error(error.response?.data?.detail || 'Failed to fetch notifications')
    }
  },

  // Get unread count for a recipient
  getUnreadCount: async (recipientId) => {
    try {
      const response = await api.get(`/notifications/unread-count?recipient_id=${recipientId}`)
      return response.data.unread_count
    } catch (error) {
      throw new Error(error.response?.data?.detail || 'Failed to fetch unread count')
    }
  },

  // Create a new notification
  createNotification: async (notificationData) => {
    try {
      const response = await api.post('/notifications', notificationData)
      return response.data
    } catch (error) {
      throw new Error(error.response?.data?.detail || 'Failed to create notification')
    }
  },

  // Mark a notification as read
  markAsRead: async (notificationId) => {
    try {
      const response = await api.post(`/notifications/mark-as-read/${notificationId}`)
      return response.data
    } catch (error) {
      throw new Error(error.response?.data?.detail || 'Failed to mark notification as read')
    }
  },

  // Mark a notification as unread
  markAsUnread: async (notificationId) => {
    try {
      const response = await api.post(`/notifications/mark-as-unread/${notificationId}`)
      return response.data
    } catch (error) {
      throw new Error(error.response?.data?.detail || 'Failed to mark notification as unread')
    }
  },

  // Mark all notifications as read for a recipient
  markAllAsRead: async (recipientId) => {
    try {
      const response = await api.post('/notifications/mark-all-as-read', { recipient_id: recipientId })
      return response.data
    } catch (error) {
      throw new Error(error.response?.data?.detail || 'Failed to mark all notifications as read')
    }
  },

  // Delete a notification
  deleteNotification: async (notificationId) => {
    try {
      await api.delete(`/notifications/${notificationId}`)
      return true
    } catch (error) {
      throw new Error(error.response?.data?.detail || 'Failed to delete notification')
    }
  },

  // Clear all notifications for a recipient
  clearAllNotifications: async (recipientId) => {
    try {
      await api.delete('/notifications/clear-all', { data: { recipient_id: recipientId } })
      return true
    } catch (error) {
      throw new Error(error.response?.data?.detail || 'Failed to clear notifications')
    }
  }
}