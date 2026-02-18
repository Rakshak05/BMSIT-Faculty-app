import { useEffect, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import syncService from '../services/syncService';

/**
 * Custom hook for automatic database synchronization when entering pages
 * @param {Object} options - Configuration options
 * @param {boolean} options.syncUser - Whether to sync user data
 * @param {boolean} options.syncFaculty - Whether to sync faculty data
 * @param {string} options.customEndpoint - Custom endpoint to sync
 * @param {Function} options.onSyncComplete - Callback when sync completes
 * @param {Function} options.onSyncError - Callback when sync fails
 */
export const usePageSync = (options = {}) => {
    const { updateUser } = useAuth();
    
    const {
        syncUser = true,
        syncFaculty = false,
        customEndpoint = null,
        onSyncComplete = null,
        onSyncError = null
    } = options;

    const performSync = useCallback(async () => {
        try {
            const results = {};
            
            // Sync user data if requested
            if (syncUser) {
                const userData = await syncService.syncUserData();
                updateUser(userData);
                results.userData = userData;
            }
            
            // Sync faculty data if requested
            if (syncFaculty) {
                const facultyData = await syncService.syncFacultyData();
                results.facultyData = facultyData;
            }
            
            // Sync custom endpoint if provided
            if (customEndpoint) {
                const customData = await syncService.syncEndpoint(customEndpoint);
                results.customData = customData;
            }
            
            // Call completion callback if provided
            if (onSyncComplete) {
                onSyncComplete(results);
            }
            
            return results;
        } catch (error) {
            console.error('Page sync failed:', error);
            
            // Call error callback if provided
            if (onSyncError) {
                onSyncError(error);
            }
            
            throw error;
        }
    }, [syncUser, syncFaculty, customEndpoint, onSyncComplete, onSyncError, updateUser]);

    // Auto-sync when component mounts
    useEffect(() => {
        performSync();
    }, [performSync]);

    return { performSync };
};

export default usePageSync;