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

export const authService = {
    /**
     * Login user with email and password
     */
    async login(email, password) {
        const response = await api.post('/auth/login', { email, password });
        const { access_token, user } = response.data;

        // Store token and user data
        localStorage.setItem(config.tokenKey, access_token);
        localStorage.setItem(config.userKey, JSON.stringify(user));

        return { token: access_token, user };
    },

    /**
     * Complete first-time setup
     */
    async firstTimeSetup(tempPassword, newPassword, phone, bio) {
        const response = await api.post('/auth/first-time-setup', {
            temp_password: tempPassword,
            new_password: newPassword,
            phone,
            bio,
        });

        // Update stored user data
        localStorage.setItem(config.userKey, JSON.stringify(response.data));

        return response.data;
    },

    /**
     * Change password
     */
    async changePassword(oldPassword, newPassword) {
        const response = await api.post('/auth/change-password', {
            old_password: oldPassword,
            new_password: newPassword,
        });
        return response.data;
    },

    /**
     * Update user profile
     */
    async updateProfile(profileData) {
        const response = await api.put('/auth/update-profile', profileData);
        
        // Update stored user data
        localStorage.setItem(config.userKey, JSON.stringify(response.data));
        
        return response.data;
    },

    /**
     * Get current user info
     */
    async getCurrentUser() {
        const response = await api.get('/auth/me');
        localStorage.setItem(config.userKey, JSON.stringify(response.data));
        return response.data;
    },

    /**
     * Verify token validity
     */
    async verifyToken() {
        try {
            const response = await api.post('/auth/verify-token');
            return response.data.valid;
        } catch (error) {
            return false;
        }
    },

    /**
     * Logout user
     */
    logout() {
        localStorage.removeItem(config.tokenKey);
        localStorage.removeItem(config.userKey);
    },

    /**
     * Check if user is authenticated
     */
    isAuthenticated() {
        return !!localStorage.getItem(config.tokenKey);
    },

    /**
     * Get stored user data
     */
    getStoredUser() {
        const userStr = localStorage.getItem(config.userKey);
        return userStr ? JSON.parse(userStr) : null;
    },

    /**
     * Validate password strength
     */
    validatePassword(password) {
        const errors = [];

        if (password.length < config.passwordRules.minLength) {
            errors.push(`Password must be at least ${config.passwordRules.minLength} characters long`);
        }

        if (config.passwordRules.requireUppercase && !/[A-Z]/.test(password)) {
            errors.push('Password must contain at least one uppercase letter');
        }

        if (config.passwordRules.requireLowercase && !/[a-z]/.test(password)) {
            errors.push('Password must contain at least one lowercase letter');
        }

        if (config.passwordRules.requireNumber && !/\d/.test(password)) {
            errors.push('Password must contain at least one number');
        }

        if (config.passwordRules.requireSpecial && !/[!@#$%^&*()_+\-=\[\]{}|;:,.<>?]/.test(password)) {
            errors.push('Password must contain at least one special character');
        }

        return {
            isValid: errors.length === 0,
            errors,
        };
    },

    /**
     * Calculate password strength (0-100)
     */
    getPasswordStrength(password) {
        let strength = 0;

        if (password.length >= 8) strength += 20;
        if (password.length >= 12) strength += 10;
        if (/[a-z]/.test(password)) strength += 20;
        if (/[A-Z]/.test(password)) strength += 20;
        if (/\d/.test(password)) strength += 15;
        if (/[!@#$%^&*()_+\-=\[\]{}|;:,.<>?]/.test(password)) strength += 15;

        return Math.min(strength, 100);
    },
};

export default authService;