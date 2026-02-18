import axios from 'axios';
import config from '../config/config';

const api = axios.create({
    baseURL: config.apiBaseUrl,
});

// Add token to requests
api.interceptors.request.use((requestConfig) => {
    const token = localStorage.getItem(config.tokenKey);
    if (token) {
        requestConfig.headers.Authorization = `Bearer ${token}`;
    }
    return requestConfig;
});

export const facultyService = {
    /**
     * Create new faculty member (Admin only)
     */
    async createFaculty(facultyData) {
        const response = await api.post('/faculty', facultyData);
        return response.data;
    },

    /**
     * Get all faculty members
     */
    async getAllFaculty(filters = {}) {
        const params = new URLSearchParams();
        if (filters.department) params.append('department', filters.department);
        if (filters.designation) params.append('designation', filters.designation);
        if (filters.skip) params.append('skip', filters.skip);
        if (filters.limit) params.append('limit', filters.limit);

        const response = await api.get(`/faculty?${params.toString()}`);
        return response.data;
    },

    /**
     * Get faculty members pending first-time setup
     */
    async getPendingSetupFaculty() {
        const response = await api.get('/faculty/pending-setup');
        return response.data;
    },

    /**
     * Get specific faculty member
     */
    async getFaculty(id) {
        const response = await api.get(`/faculty/${id}`);
        return response.data;
    },

    /**
     * Update faculty member
     */
    async updateFaculty(id, updateData) {
        const response = await api.put(`/faculty/${id}`, updateData);
        return response.data;
    },

    /**
     * Delete faculty member
     */
    async deleteFaculty(id) {
        await api.delete(`/faculty/${id}`);
    },

    /**
     * Resend credentials to faculty
     */
    async resendCredentials(id) {
        const response = await api.post(`/faculty/${id}/resend-credentials`);
        return response.data;
    },
};

export default facultyService;
