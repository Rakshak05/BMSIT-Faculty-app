import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { FiArrowLeft } from 'react-icons/fi';
import { toast } from 'sonner';
import facultyService from '../services/facultyService';
import usePageSync from '../hooks/usePageSync';

const AddFacultyScreen = () => {
  const navigate = useNavigate();
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    department: '',
    designation: '',
    employee_id: '',
    phone: ''
  });
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);

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

  const departments = [
    "Artificial Intelligence & Machine Learning (AIML)",
    "Civil",
    "Computer Science (CSE)",
    "Computer Science & Business Systems (CSBS)",
    "Electrical & Electronics (EEE)",
    "Electronics & Comm (ECE)",
    "Electronics & Telecomm (ETE)",
    "Information Science (ISE)",
    "Mechanical (MECH)"
  ];

  const designations = [
    "Professor",
    "Associate Professor",
    "Assistant Professor",
    "Lecturer",
    "Lab Instructor"
  ];

  const validateForm = () => {
    const newErrors = {};

    // Basic validation
    if (!formData.name.trim()) {
      newErrors.name = "Name is required";
    }

    if (!formData.email.trim()) {
      newErrors.email = "Email is required";
    } else if (!formData.email.endsWith('@bmsit.in')) {
      newErrors.email = "Email must end with @bmsit.in";
    }

    if (!formData.department) {
      newErrors.department = "Department is required";
    }

    if (!formData.designation) {
      newErrors.designation = "Designation is required";
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));

    // Clear error when user starts typing
    if (errors[name]) {
      setErrors(prev => ({
        ...prev,
        [name]: ''
      }));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) {
      return;
    }

    setLoading(true);

    try {
      // Prepare data for API
      const facultyData = {
        name: formData.name,
        email: formData.email,
        department: formData.department,
        designation: formData.designation,
        employee_id: formData.employee_id || null,
        phone: formData.phone || null,
        role: 'faculty'
      };

      // Call API to create faculty
      await facultyService.createFaculty(facultyData);

      // Show success message
      toast.success('Faculty member added successfully! Welcome email sent.');

      // Reset form
      setFormData({
        name: '',
        email: '',
        department: '',
        designation: '',
        employee_id: '',
        phone: ''
      });

      // Navigate back to dashboard
      setTimeout(() => navigate('/'), 1500);
    } catch (error) {
      console.error('Error creating faculty:', error);
      const errorMessage = error.response?.data?.detail || 'Failed to add faculty member. Please try again.';
      toast.error(errorMessage);
      setErrors({ general: errorMessage });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex h-screen bg-gray-900 text-white">
      {/* Main Content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top Navigation Bar */}
        <div className="flex items-center justify-between p-4 bg-gray-800 border-b border-gray-700">
          <div className="flex items-center space-x-4">
            <button
              onClick={() => navigate('/')}
              className="p-2 rounded-full hover:bg-gray-700 transition"
            >
              <FiArrowLeft size={20} />
            </button>
            <h1 className="text-xl font-bold">Add New Faculty</h1>
          </div>
        </div>

        {/* Form Content */}
        <div className="flex-1 overflow-auto p-6">
          <div className="max-w-2xl mx-auto">
            <form onSubmit={handleSubmit} className="space-y-6">
              {/* General Error */}
              {errors.general && (
                <div className="p-3 bg-red-500/10 border border-red-500 rounded text-red-500 text-sm">
                  {errors.general}
                </div>
              )}

              {/* Name Input */}
              <div>
                <label className="block text-lg font-medium mb-2">Full Name</label>
                <input
                  type="text"
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
                  placeholder="Enter Name"
                  disabled={loading}
                  className={`w-full p-3 rounded bg-gray-800 border ${errors.name ? 'border-red-500' : 'border-gray-700'
                    } focus:outline-none focus:ring-2 focus:ring-blue-500`}
                />
                {errors.name && <p className="text-red-500 text-sm mt-1">{errors.name}</p>}
              </div>

              {/* Email Input */}
              <div>
                <label className="block text-lg font-medium mb-2">Email ID</label>
                <input
                  type="email"
                  name="email"
                  value={formData.email}
                  onChange={handleChange}
                  placeholder="example@bmsit.in"
                  disabled={loading}
                  className={`w-full p-3 rounded bg-gray-800 border ${errors.email ? 'border-red-500' : 'border-gray-700'
                    } focus:outline-none focus:ring-2 focus:ring-blue-500`}
                />
                {errors.email && <p className="text-red-500 text-sm mt-1">{errors.email}</p>}
              </div>

              {/* Department Select */}
              <div>
                <label className="block text-lg font-medium mb-2">Department</label>
                <select
                  name="department"
                  value={formData.department}
                  onChange={handleChange}
                  disabled={loading}
                  className={`w-full p-3 rounded bg-gray-800 border ${errors.department ? 'border-red-500' : 'border-gray-700'
                    } focus:outline-none focus:ring-2 focus:ring-blue-500`}
                >
                  <option value="">Select Department</option>
                  {departments.map((dept, index) => (
                    <option key={index} value={dept}>
                      {dept}
                    </option>
                  ))}
                </select>
                {errors.department && <p className="text-red-500 text-sm mt-1">{errors.department}</p>}
              </div>

              {/* Designation Select */}
              <div>
                <label className="block text-lg font-medium mb-2">Designation</label>
                <select
                  name="designation"
                  value={formData.designation}
                  onChange={handleChange}
                  disabled={loading}
                  className={`w-full p-3 rounded bg-gray-800 border ${errors.designation ? 'border-red-500' : 'border-gray-700'
                    } focus:outline-none focus:ring-2 focus:ring-blue-500`}
                >
                  <option value="">Select Designation</option>
                  {designations.map((designation, index) => (
                    <option key={index} value={designation}>
                      {designation}
                    </option>
                  ))}
                </select>
                {errors.designation && <p className="text-red-500 text-sm mt-1">{errors.designation}</p>}
              </div>

              {/* Employee ID Input (Optional) */}
              <div>
                <label className="block text-lg font-medium mb-2">
                  Employee ID <span className="text-gray-400 text-sm">(Optional)</span>
                </label>
                <input
                  type="text"
                  name="employee_id"
                  value={formData.employee_id}
                  onChange={handleChange}
                  placeholder="Enter Employee ID"
                  disabled={loading}
                  className="w-full p-3 rounded bg-gray-800 border border-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              {/* Phone Input (Optional) */}
              <div>
                <label className="block text-lg font-medium mb-2">
                  Phone Number <span className="text-gray-400 text-sm">(Optional)</span>
                </label>
                <input
                  type="tel"
                  name="phone"
                  value={formData.phone}
                  onChange={handleChange}
                  placeholder="+91 1234567890"
                  disabled={loading}
                  className="w-full p-3 rounded bg-gray-800 border border-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              {/* Submit Button */}
              <div className="flex justify-center pt-4">
                <button
                  type="submit"
                  disabled={loading}
                  className={`bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 px-8 rounded transition duration-200 ${loading ? 'opacity-50 cursor-not-allowed' : ''
                    }`}
                >
                  {loading ? 'Adding Member...' : 'Add Member'}
                </button>
              </div>

              {/* Info Message */}
              <div className="text-center text-gray-400 text-sm">
                <p>A welcome email with temporary password will be sent to the faculty member.</p>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AddFacultyScreen;