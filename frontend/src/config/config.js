const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000/api/v1';

export const config = {
  apiBaseUrl: API_BASE_URL,
  tokenKey: 'bmsit_auth_token',
  userKey: 'bmsit_user',
  
  // Password validation rules
  passwordRules: {
    minLength: 8,
    requireUppercase: true,
    requireLowercase: true,
    requireNumber: true,
    requireSpecial: true,
  },
};

export default config;
