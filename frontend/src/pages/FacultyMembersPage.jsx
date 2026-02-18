import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { FiArrowLeft, FiUsers, FiSearch, FiFilter, FiMenu } from 'react-icons/fi';
import usePageSync from '../hooks/usePageSync';
import facultyService from '../services/facultyService';

const FacultyMembersPage = () => {
  const navigate = useNavigate();
  const [facultyMembers, setFacultyMembers] = useState([]);
  const [filteredMembers, setFilteredMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);

  // Use the new page sync hook for automatic database synchronization
  const { performSync } = usePageSync({
    syncUser: true,
    syncFaculty: true,
    onSyncComplete: (results) => {
      console.log('Page sync completed successfully', results);
      if (results.facultyData) {
        setFacultyMembers(results.facultyData);
        setFilteredMembers(results.facultyData);
      }
    },
    onSyncError: (error) => {
      console.error('Page sync failed', error);
    }
  });

  useEffect(() => {
    const fetchFacultyMembers = async () => {
      try {
        setLoading(true);
        const members = await facultyService.getAllFaculty();
        setFacultyMembers(members);
        setFilteredMembers(members);
      } catch (error) {
        console.error('Error fetching faculty members:', error);
      } finally {
        setLoading(false);
      }
    };

    fetchFacultyMembers();
  }, []);

  useEffect(() => {
    const filtered = facultyMembers.filter(member =>
      member.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      member.department.toLowerCase().includes(searchTerm.toLowerCase()) ||
      member.designation.toLowerCase().includes(searchTerm.toLowerCase())
    );
    setFilteredMembers(filtered);
  }, [searchTerm, facultyMembers]);

  const handleBack = () => {
    navigate('/');
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
            <h1 className="text-xl font-bold">Faculty Members</h1>
          </div>
          
          <div className="flex items-center space-x-4">
            <div className="relative">
              <FiSearch className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" size={18} />
              <input
                type="text"
                placeholder="Search faculty..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10 pr-4 py-2 bg-gray-700 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <button className="p-2 rounded-full hover:bg-gray-700 transition">
              <FiFilter size={20} />
            </button>
          </div>
        </div>

        {/* Faculty Members List */}
        <div className="flex-1 overflow-auto p-6">
          {loading ? (
            <div className="flex items-center justify-center h-full">
              <div className="text-lg">Loading faculty members...</div>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
              {filteredMembers.map((member) => (
                <div key={member._id} className="bg-gray-800 rounded-lg border border-gray-700 p-6 hover:bg-gray-750 transition">
                  <div className="flex items-center space-x-4 mb-4">
                    <div className="bg-gray-600 rounded-full w-12 h-12 flex items-center justify-center">
                      <FiUsers size={24} />
                    </div>
                    <div>
                      <h3 className="font-bold text-lg">{member.name}</h3>
                      <p className="text-gray-400 text-sm">{member.email}</p>
                    </div>
                  </div>
                  
                  <div className="space-y-2">
                    <div className="flex justify-between">
                      <span className="text-gray-400">Department:</span>
                      <span>{member.department}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-gray-400">Designation:</span>
                      <span>{member.designation}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-gray-400">Employee ID:</span>
                      <span>{member.employee_id || 'N/A'}</span>
                    </div>
                  </div>
                </div>
              ))}
              
              {filteredMembers.length === 0 && (
                <div className="col-span-full text-center py-12">
                  <FiUsers size={48} className="mx-auto text-gray-500 mb-4" />
                  <h3 className="text-xl font-semibold mb-2">No faculty members found</h3>
                  <p className="text-gray-400">
                    {searchTerm ? 'Try adjusting your search criteria' : 'There are no faculty members in the system'}
                  </p>
                </div>
              )}
            </div>
          )}
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

export default FacultyMembersPage;