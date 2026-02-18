import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import authService from '../services/authService';
import { toast } from 'sonner';
import { FiCheck, FiX } from 'react-icons/fi';
import usePageSync from '../hooks/usePageSync';

const FirstTimeSetup = () => {
    const navigate = useNavigate();
    const { user, updateUser } = useAuth();
    const [step, setStep] = useState(1);
    const [loading, setLoading] = useState(false);
    const [formData, setFormData] = useState({
        tempPassword: '',
        newPassword: '',
        confirmPassword: '',
        phone: '',
        bio: '',
    });
    const [errors, setErrors] = useState({});

    // Use the new page sync hook for automatic database synchronization
    const { performSync } = usePageSync({
        syncUser: true,
        onSyncComplete: (results) => {
            console.log('Page sync completed successfully', results);
        },
        onSyncError: (error) => {
            console.error('Page sync failed', error);
        }
    });

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData((prev) => ({
            ...prev,
            [name]: value,
        }));

        // Clear error when user starts typing
        if (errors[name]) {
            setErrors((prev) => ({
                ...prev,
                [name]: '',
            }));
        }
    };

    const getPasswordStrength = () => {
        return authService.getPasswordStrength(formData.newPassword);
    };

    const getPasswordStrengthColor = () => {
        const strength = getPasswordStrength();
        if (strength < 40) return 'bg-red-500';
        if (strength < 70) return 'bg-yellow-500';
        return 'bg-green-500';
    };

    const getPasswordStrengthLabel = () => {
        const strength = getPasswordStrength();
        if (strength < 40) return 'Weak';
        if (strength < 70) return 'Medium';
        return 'Strong';
    };

    const validateStep1 = () => {
        const newErrors = {};

        if (!formData.tempPassword) {
            newErrors.tempPassword = 'Temporary password is required';
        }

        if (!formData.newPassword) {
            newErrors.newPassword = 'New password is required';
        } else {
            const validation = authService.validatePassword(formData.newPassword);
            if (!validation.isValid) {
                newErrors.newPassword = validation.errors[0];
            }
        }

        if (!formData.confirmPassword) {
            newErrors.confirmPassword = 'Please confirm your password';
        } else if (formData.newPassword !== formData.confirmPassword) {
            newErrors.confirmPassword = 'Passwords do not match';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleContinue = () => {
        if (validateStep1()) {
            setStep(2);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!validateStep1()) {
            setStep(1);
            return;
        }

        setLoading(true);

        try {
            const updatedUser = await authService.firstTimeSetup(
                formData.tempPassword,
                formData.newPassword,
                formData.phone || null,
                formData.bio || null
            );

            updateUser(updatedUser);
            toast.success('Account setup completed successfully!');
            navigate('/');
        } catch (error) {
            console.error('Setup error:', error);
            const errorMessage = error.response?.data?.detail || 'Setup failed. Please try again.';
            toast.error(errorMessage);
            setErrors({ general: errorMessage });
            setStep(1);
        } finally {
            setLoading(false);
        }
    };

    const passwordRequirements = [
        { label: 'At least 8 characters', test: (pwd) => pwd.length >= 8 },
        { label: 'One uppercase letter', test: (pwd) => /[A-Z]/.test(pwd) },
        { label: 'One lowercase letter', test: (pwd) => /[a-z]/.test(pwd) },
        { label: 'One number', test: (pwd) => /\d/.test(pwd) },
        { label: 'One special character', test: (pwd) => /[!@#$%^&*()_+\-=\[\]{}|;:,.<>?]/.test(pwd) },
    ];

    return (
        <div className="flex items-center justify-center min-h-screen bg-gradient-to-br from-gray-900 via-purple-900 to-gray-900 p-4">
            <div className="w-full max-w-2xl p-8 bg-gray-800 rounded-lg shadow-2xl border border-gray-700">
                {/* Header */}
                <div className="text-center mb-8">
                    <h1 className="text-3xl font-bold text-white mb-2">
                        Welcome, {user?.name}! 👋
                    </h1>
                    <p className="text-gray-400">Let's set up your account</p>
                </div>

                {/* Progress Indicator */}
                <div className="flex items-center justify-center mb-8">
                    <div className="flex items-center space-x-4">
                        <div className={`flex items-center justify-center w-10 h-10 rounded-full ${step >= 1 ? 'bg-blue-600' : 'bg-gray-600'
                            } text-white font-semibold`}>
                            1
                        </div>
                        <div className={`h-1 w-16 ${step >= 2 ? 'bg-blue-600' : 'bg-gray-600'}`} />
                        <div className={`flex items-center justify-center w-10 h-10 rounded-full ${step >= 2 ? 'bg-blue-600' : 'bg-gray-600'
                            } text-white font-semibold`}>
                            2
                        </div>
                    </div>
                </div>

                <form onSubmit={handleSubmit} className="space-y-6">
                    {/* General Error */}
                    {errors.general && (
                        <div className="p-3 bg-red-500/10 border border-red-500 rounded text-red-500 text-sm">
                            {errors.general}
                        </div>
                    )}

                    {/* Step 1: Password Setup */}
                    {step === 1 && (
                        <div className="space-y-6">
                            <h2 className="text-xl font-semibold text-white mb-4">Step 1: Set Your Password</h2>

                            {/* Temporary Password */}
                            <div>
                                <label className="block text-sm font-medium text-gray-300 mb-2">
                                    Temporary Password
                                </label>
                                <input
                                    type="password"
                                    name="tempPassword"
                                    value={formData.tempPassword}
                                    onChange={handleChange}
                                    placeholder="Enter temporary password from email"
                                    className={`w-full px-4 py-3 bg-gray-700 border ${errors.tempPassword ? 'border-red-500' : 'border-gray-600'
                                        } rounded-lg text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 transition`}
                                />
                                {errors.tempPassword && (
                                    <p className="text-red-500 text-sm mt-1">{errors.tempPassword}</p>
                                )}
                            </div>

                            {/* New Password */}
                            <div>
                                <label className="block text-sm font-medium text-gray-300 mb-2">
                                    New Password
                                </label>
                                <input
                                    type="password"
                                    name="newPassword"
                                    value={formData.newPassword}
                                    onChange={handleChange}
                                    placeholder="Create a strong password"
                                    className={`w-full px-4 py-3 bg-gray-700 border ${errors.newPassword ? 'border-red-500' : 'border-gray-600'
                                        } rounded-lg text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 transition`}
                                />
                                {errors.newPassword && (
                                    <p className="text-red-500 text-sm mt-1">{errors.newPassword}</p>
                                )}

                                {/* Password Strength Indicator */}
                                {formData.newPassword && (
                                    <div className="mt-2">
                                        <div className="flex items-center justify-between mb-1">
                                            <span className="text-xs text-gray-400">Password Strength:</span>
                                            <span className="text-xs text-gray-400">{getPasswordStrengthLabel()}</span>
                                        </div>
                                        <div className="h-2 bg-gray-600 rounded-full overflow-hidden">
                                            <div
                                                className={`h-full ${getPasswordStrengthColor()} transition-all duration-300`}
                                                style={{ width: `${getPasswordStrength()}%` }}
                                            />
                                        </div>
                                    </div>
                                )}
                            </div>

                            {/* Confirm Password */}
                            <div>
                                <label className="block text-sm font-medium text-gray-300 mb-2">
                                    Confirm New Password
                                </label>
                                <input
                                    type="password"
                                    name="confirmPassword"
                                    value={formData.confirmPassword}
                                    onChange={handleChange}
                                    placeholder="Re-enter your password"
                                    className={`w-full px-4 py-3 bg-gray-700 border ${errors.confirmPassword ? 'border-red-500' : 'border-gray-600'
                                        } rounded-lg text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 transition`}
                                />
                                {errors.confirmPassword && (
                                    <p className="text-red-500 text-sm mt-1">{errors.confirmPassword}</p>
                                )}
                            </div>

                            {/* Password Requirements */}
                            <div className="bg-gray-700/50 p-4 rounded-lg">
                                <p className="text-sm font-medium text-gray-300 mb-2">Password Requirements:</p>
                                <ul className="space-y-1">
                                    {passwordRequirements.map((req, index) => {
                                        const isMet = req.test(formData.newPassword);
                                        return (
                                            <li key={index} className="flex items-center text-sm">
                                                {isMet ? (
                                                    <FiCheck className="text-green-500 mr-2" />
                                                ) : (
                                                    <FiX className="text-gray-500 mr-2" />
                                                )}
                                                <span className={isMet ? 'text-green-500' : 'text-gray-400'}>
                                                    {req.label}
                                                </span>
                                            </li>
                                        );
                                    })}
                                </ul>
                            </div>

                            <button
                                type="button"
                                onClick={handleContinue}
                                className="w-full py-3 px-4 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-lg transition duration-200"
                            >
                                Continue
                            </button>
                        </div>
                    )}

                    {/* Step 2: Profile Information */}
                    {step === 2 && (
                        <div className="space-y-6">
                            <h2 className="text-xl font-semibold text-white mb-4">
                                Step 2: Complete Your Profile (Optional)
                            </h2>

                            {/* Phone */}
                            <div>
                                <label className="block text-sm font-medium text-gray-300 mb-2">
                                    Phone Number
                                </label>
                                <input
                                    type="tel"
                                    name="phone"
                                    value={formData.phone}
                                    onChange={handleChange}
                                    placeholder="+91 1234567890"
                                    className="w-full px-4 py-3 bg-gray-700 border border-gray-600 rounded-lg text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 transition"
                                />
                            </div>

                            {/* Bio */}
                            <div>
                                <label className="block text-sm font-medium text-gray-300 mb-2">
                                    Bio
                                </label>
                                <textarea
                                    name="bio"
                                    value={formData.bio}
                                    onChange={handleChange}
                                    placeholder="Tell us a bit about yourself..."
                                    rows={4}
                                    className="w-full px-4 py-3 bg-gray-700 border border-gray-600 rounded-lg text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 transition resize-none"
                                />
                            </div>

                            <div className="flex space-x-4">
                                <button
                                    type="button"
                                    onClick={() => setStep(1)}
                                    className="flex-1 py-3 px-4 bg-gray-600 hover:bg-gray-700 text-white font-semibold rounded-lg transition duration-200"
                                >
                                    Back
                                </button>
                                <button
                                    type="submit"
                                    disabled={loading}
                                    className={`flex-1 py-3 px-4 bg-green-600 hover:bg-green-700 text-white font-semibold rounded-lg transition duration-200 ${loading ? 'opacity-50 cursor-not-allowed' : ''
                                        }`}
                                >
                                    {loading ? 'Completing Setup...' : 'Complete Setup'}
                                </button>
                            </div>
                        </div>
                    )}
                </form>
            </div>
        </div>
    );
};

export default FirstTimeSetup;
