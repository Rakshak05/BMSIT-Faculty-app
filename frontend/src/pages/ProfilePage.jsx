import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { FiArrowLeft, FiUser, FiMail, FiPhone, FiBriefcase, FiMenu, FiLogOut, FiEdit2 } from 'react-icons/fi';
import usePageSync from '../hooks/usePageSync';
import authService from '../services/authService';

const ProfilePage = () => {
  const navigate = useNavigate();
  const { user, logout, updateUser } = useAuth();
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [isEditingName, setIsEditingName] = useState(false);
  const [newName, setNewName] = useState(user?.name || '');

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

  const handleBack = () => {
    navigate('/');
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const handleNameEdit = () => {
    setIsEditingName(true);
    setNewName(user?.name || '');
  };

  const handleNameSave = async () => {
    try {
      if (newName.trim() && newName !== user?.name) {
        const updatedUser = await authService.updateProfile({ name: newName.trim() });
        updateUser(updatedUser);
      }
      setIsEditingName(false);
    } catch (error) {
      console.error('Error updating name:', error);
      alert('Failed to update name. Please try again.');
    }
  };

  const handleNameCancel = () => {
    setIsEditingName(false);
    setNewName(user?.name || '');
  };

  return (
    <div className="flex h-screen bg-gray-900 text-white">
      {/* Main Content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top Navigation Bar */}
        <div className="flex items-center justify-between p-4 bg-gray-800 border-b border-gray-700">
          <div className="flex items-center space-x-4">
            <button
              onClick={() => setIsSidebarOpen(!isSidebarOpen)}
              className="p-2 rounded-full hover:bg-gray-700 transition md:hidden"
            >
              <FiMenu size={20} />
            </button>
            <button
              onClick={handleBack}
              className="p-2 rounded-full hover:bg-gray-700 transition"
            >
              <FiArrowLeft size={20} />
            </button>
            <h1 className="text-xl font-bold">Profile</h1>
          </div>
        </div>

        {/* Profile Content */}
        <div className="flex-1 overflow-auto p-6">
          <div className="max-w-4xl mx-auto">
            <div className="bg-gray-800 rounded-lg border border-gray-700 p-8 mb-8">
              <div className="flex flex-col md:flex-row items-center md:items-start space-y-6 md:space-y-0 md:space-x-8">
                <div className="bg-gray-700 rounded-full w-24 h-24 flex items-center justify-center">
                  <FiUser size={48} />
                </div>
                
                <div className="flex-1 text-center md:text-left">
                  <div className="flex items-center justify-center md:justify-start space-x-2">
                    {isEditingName ? (
                      <div className="flex items-center space-x-2">
                        <input
                          type="text"
                          value={newName}
                          onChange={(e) => setNewName(e.target.value)}
                          className="bg-gray-700 text-white px-3 py-1 rounded"
                          autoFocus
                        />
                        <button 
                          onClick={handleNameSave}
                          className="text-green-500 hover:text-green-400"
                        >
                          Save
                        </button>
                        <button 
                          onClick={handleNameCancel}
                          className="text-red-500 hover:text-red-400"
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <>
                        <h2 className="text-2xl font-bold mb-2">{user?.name}</h2>
                        <button 
                          onClick={handleNameEdit}
                          className="text-gray-400 hover:text-gray-300"
                        >
                          <FiEdit2 size={16} />
                        </button>
                      </>
                    )}
                  </div>
                  <p className="text-gray-400 mb-4">{user?.email}</p>
                  
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
                    <div className="bg-gray-750 rounded-lg p-4">
                      <div className="flex items-center text-gray-400 mb-2">
                        <FiBriefcase className="mr-2" size={18} />
                        <span>Department</span>
                      </div>
                      <p className="font-medium">{user?.department || 'Not specified'}</p>
                    </div>
                    
                    <div className="bg-gray-750 rounded-lg p-4">
                      <div className="flex items-center text-gray-400 mb-2">
                        <FiBriefcase className="mr-2" size={18} />
                        <span>Designation</span>
                      </div>
                      <p className="font-medium">{user?.designation || 'Not specified'}</p>
                    </div>
                    
                    <div className="bg-gray-750 rounded-lg p-4">
                      <div className="flex items-center text-gray-400 mb-2">
                        <FiPhone className="mr-2" size={18} />
                        <span>Phone</span>
                      </div>
                      <p className="font-medium">{user?.phone || 'Not specified'}</p>
                    </div>
                    
                    <div className="bg-gray-750 rounded-lg p-4">
                      <div className="flex items-center text-gray-400 mb-2">
                        <FiMail className="mr-2" size={18} />
                        <span>Role</span>
                      </div>
                      <p className="font-medium capitalize">{user?.role || 'Not specified'}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* Danger Zone */}
            <div className="bg-red-900/20 border border-red-800 rounded-lg p-6">
              <h3 className="text-xl font-bold mb-4 text-red-400">Danger Zone</h3>
              <p className="text-gray-300 mb-6">
                Logging out will end your session and require you to sign in again.
              </p>
              <button
                onClick={handleLogout}
                className="flex items-center px-4 py-2 bg-red-600 hover:bg-red-700 rounded-lg transition"
              >
                <FiLogOut className="mr-2" size={18} />
                <span>Log Out</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Overlay for mobile sidebar */}
      {isSidebarOpen && (
        <div 
          className="fixed inset-0 bg-black bg-opacity-50 z-20 md:hidden"
          onClick={() => setIsSidebarOpen(false)}
        ></div>
      )}
    </div>
  );
};

export default ProfilePage;