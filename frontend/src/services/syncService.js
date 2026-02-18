import axios from 'axios';
import config from '../config/config';

const api = axios.create({
    baseURL: config.apiBaseUrl,
});

// Add token to requests if available
api.interceptors.request.use((requestConfig) => {
    const token = localStorage.getItem(config.tokenKey);
    if (token) {
        requestConfig.headers.Authorization = `Bearer ${token}`;
    }
    return requestConfig;
});

/**
 * Sync Service for automatic database synchronization when entering pages
 */
export const syncService = {
    /**
     * Sync user data with the database
     */
    async syncUserData() {
        try {
            const response = await api.get('/auth/me');
            const userData = response.data;
            
            // Update local storage with fresh data
            localStorage.setItem(config.userKey, JSON.stringify(userData));
            
            return userData;
        } catch (error) {
            console.error('Sync failed:', error);
            throw error;
        }
    },

    /**
     * Sync faculty data with the database
     */
    async syncFacultyData() {
        try {
            const response = await api.get('/faculty/');
            return response.data;
        } catch (error) {
            console.error('Faculty sync failed:', error);
            throw error;
        }
    },

    /**
     * Sync specific faculty member data
     */
    async syncFacultyMember(facultyId) {
        try {
            const response = await api.get(`/faculty/${facultyId}`);
            return response.data;
        } catch (error) {
            console.error('Faculty member sync failed:', error);
            throw error;
        }
    },

    /**
     * Generic sync function for any endpoint
     */
    async syncEndpoint(endpoint) {
        try {
            const response = await api.get(endpoint);
            return response.data;
        } catch (error) {
            console.error(`Sync failed for ${endpoint}:`, error);
            throw error;
        }
    }
};

export default syncService;