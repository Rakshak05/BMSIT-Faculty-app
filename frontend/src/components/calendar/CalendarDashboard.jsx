import React, { useState, useEffect } from 'react';
import { format, startOfWeek, endOfWeek, startOfMonth, endOfMonth, eachDayOfInterval, isSameDay, addDays, subDays, addWeeks, subWeeks, addMonths, subMonths, parseISO } from 'date-fns';
import { FiChevronLeft, FiChevronRight, FiPlus, FiCalendar, FiClipboard, FiFileText, FiX, FiClock, FiMapPin, FiUsers, FiMenu, FiDownload } from 'react-icons/fi';
import NewMeetingModal from './NewMeetingModal';

const CalendarDashboard = () => {
  const [currentDate, setCurrentDate] = useState(new Date());
  const [events, setEvents] = useState([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [expandedEvent, setExpandedEvent] = useState(null); // Track which event is expanded
  const [selectedEvent, setSelectedEvent] = useState(null);
  const [isMenuOpen, setIsMenuOpen] = useState(false); // Track hamburger menu state

  // Mock data for meetings
  const mockMeetings = [
    { 
      id: 1, 
      title: "Faculty Meeting", 
      start: "2025-04-19T10:00:00", 
      end: "2025-04-19T11:00:00", 
      participants: ["Dr. Mehta", "Prof. Rao"], 
      room: "Seminar Hall",
      color: "bg-blue-500"
    },
    { 
      id: 2, 
      title: "Department Review", 
      start: "2025-04-21T14:00:00", 
      end: "2025-04-21T15:30:00", 
      participants: ["CS Dept"], 
      room: "Room 201",
      color: "bg-green-500"
    },
    { 
      id: 3, 
      title: "Curriculum Planning", 
      start: "2025-04-20T09:00:00", 
      end: "2025-04-20T10:30:00", 
      participants: ["Dr. Kumar", "Prof. Patel", "Dr. Singh"], 
      room: "Conference Room A",
      color: "bg-purple-500"
    },
    { 
      id: 4, 
      title: "Student Feedback Session", 
      start: "2025-04-22T13:00:00", 
      end: "2025-04-22T14:00:00", 
      participants: ["Student Council"], 
      room: "Auditorium",
      color: "bg-yellow-500"
    }
  ];

  // Mock data for faculty members
  const mockFacultyMembers = [
    { 
      id: 1,
      name: "Dr. Mehta", 
      dept: "Computer Science", 
      meetingsAttended: 15, 
      meetingsMissed: 3, 
      minutesOfMeeting: 1200
    },
    { 
      id: 2,
      name: "Prof. Rao", 
      dept: "Electronics", 
      meetingsAttended: 12, 
      meetingsMissed: 6, 
      minutesOfMeeting: 960
    },
    { 
      id: 3,
      name: "Dr. Kumar", 
      dept: "Mechanical", 
      meetingsAttended: 18, 
      meetingsMissed: 2, 
      minutesOfMeeting: 1440
    },
    { 
      id: 4,
      name: "Prof. Patel", 
      dept: "Civil", 
      meetingsAttended: 10, 
      meetingsMissed: 8, 
      minutesOfMeeting: 800
    },
    { 
      id: 5,
      name: "Dr. Singh", 
      dept: "Electrical", 
      meetingsAttended: 14, 
      meetingsMissed: 4, 
      minutesOfMeeting: 1120
    }
  ];

  useEffect(() => {
    // In a real app, this would fetch from an API
    setEvents(mockMeetings);
  }, []);

  const navigateDate = (direction) => {
    setCurrentDate(direction === 'next' ? addMonths(currentDate, 1) : subMonths(currentDate, 1));
  };

  const goToToday = () => {
    setCurrentDate(new Date());
  };

  const getDaysToDisplay = () => {
    const start = startOfMonth(currentDate);
    const end = endOfMonth(currentDate);
    return eachDayOfInterval({ start, end });
  };

  const getEventsForDay = (day) => {
    return events.filter(event => 
      isSameDay(parseISO(event.start), day)
    );
  };

  const handleEventClick = (event) => {
    // Toggle expansion of the clicked event
    if (expandedEvent && expandedEvent.id === event.id) {
      setExpandedEvent(null); // Collapse if already expanded
    } else {
      setExpandedEvent(event); // Expand the clicked event
    }
  };

  const handleCreateMeeting = () => {
    setSelectedEvent(null);
    setIsModalOpen(true);
  };

  const handleSaveMeeting = (meetingData) => {
    if (selectedEvent) {
      // Update existing event
      setEvents(events.map(event => 
        event.id === selectedEvent.id ? { ...event, ...meetingData } : event
      ));
    } else {
      // Create new event
      const newEvent = {
        id: events.length + 1,
        ...meetingData,
        color: "bg-indigo-500"
      };
      setEvents([...events, newEvent]);
    }
    setIsModalOpen(false);
  };

  const handleTakeAttendance = (event) => {
    // Handle take attendance functionality
    console.log(`Taking attendance for ${event.title}`);
    // TODO: Implement take attendance functionality
  };

  const handleStartRecording = (event) => {
    // Handle start recording functionality
    console.log(`Starting recording for ${event.title}`);
    // TODO: Implement start recording functionality
  };

  const handleDownloadCSV = () => {
    // Create CSV content
    const headers = ['Name', 'Department', 'Meetings Attended', 'Meetings Missed', 'Minutes of Meeting'];
    const rows = mockFacultyMembers.map(member => [
      member.name,
      member.dept,
      member.meetingsAttended,
      member.meetingsMissed,
      member.minutesOfMeeting
    ]);

    // Combine headers and rows
    const csvContent = [
      headers.join(','),
      ...rows.map(row => row.join(','))
    ].join('\n');

    // Create download link
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', 'faculty_members.csv');
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // Function to download calendar events as CSV
  const handleDownloadEventsCSV = () => {
    // Create CSV content for events
    const headers = ['Title', 'Start Time', 'End Time', 'Room', 'Participants'];
    const rows = events.map(event => [
      event.title,
      event.start,
      event.end,
      event.room,
      event.participants.join('; ')
    ]);

    // Combine headers and rows
    const csvContent = [
      headers.join(','),
      ...rows.map(row => row.join(','))
    ].join('\n');

    // Create download link
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', 'calendar_events.csv');
    link.style.visibility = 'hidden';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const days = getDaysToDisplay();

  return (
    <div className="flex flex-col h-screen bg-gray-900 text-white">
      {/* Top Navigation Bar */}
      <div className="flex items-center justify-between p-4 bg-gray-800 border-b border-gray-700">
        <div className="flex items-center space-x-4">
          <h1 className="text-xl font-bold">Meeting Scheduler</h1>
          <div className="flex items-center space-x-2">
            <button 
              onClick={goToToday}
              className="px-3 py-1 text-sm bg-gray-700 rounded hover:bg-gray-600 transition"
            >
              Today
            </button>
            <button 
              onClick={() => navigateDate('prev')}
              className="p-2 rounded-full hover:bg-gray-700 transition"
            >
              <FiChevronLeft size={20} />
            </button>
            <button 
              onClick={() => navigateDate('next')}
              className="p-2 rounded-full hover:bg-gray-700 transition"
            >
              <FiChevronRight size={20} />
            </button>
            <h2 className="text-lg font-semibold">
              {format(currentDate, 'MMMM yyyy')}
            </h2>
          </div>
        </div>
        <div className="flex items-center space-x-4">
          {/* Download CSV Button */}
          <button 
            onClick={handleDownloadEventsCSV}
            className="flex items-center space-x-2 px-4 py-2 bg-green-600 rounded-lg hover:bg-green-700 transition"
          >
            <FiDownload size={18} />
            <span>Download CSV</span>
          </button>
          
          {/* Hamburger Menu */}
          <div className="relative">
            <button 
              onClick={() => setIsMenuOpen(!isMenuOpen)}
              className="p-2 rounded-full hover:bg-gray-700 transition"
            >
              <FiMenu size={20} />
            </button>
            
            {/* Dropdown Menu */}
            {isMenuOpen && (
              <div className="absolute right-0 mt-2 w-48 bg-gray-800 rounded-md shadow-lg py-1 z-50 border border-gray-700">
                <button
                  onClick={() => {
                    handleDownloadCSV();
                    setIsMenuOpen(false);
                  }}
                  className="flex items-center w-full px-4 py-2 text-sm hover:bg-gray-700 transition"
                >
                  <FiDownload className="mr-2" size={16} />
                  Download Faculty CSV
                </button>
              </div>
            )}
          </div>
          
          <button className="flex items-center space-x-2 px-4 py-2 bg-blue-600 rounded-lg hover:bg-blue-700 transition">
            <FiCalendar size={18} />
            <span>Manage in Calendar</span>
          </button>
        </div>
      </div>

      {/* Calendar View */}
      <div className="flex-1 overflow-auto p-4">
        <div className="grid gap-4 grid-cols-7">
          {/* Day Headers */}
          <div className="col-span-full grid grid-cols-7 gap-4 mb-2">
            {days.map((day, index) => (
              <div key={index} className="text-center p-2">
                <div className="text-gray-400 text-sm">{format(day, 'EEE')}</div>
                <div className={`text-lg ${isSameDay(day, new Date()) ? 'bg-blue-600 rounded-full w-8 h-8 flex items-center justify-center mx-auto' : ''}`}>
                  {format(day, 'd')}
                </div>
              </div>
            ))}
          </div>

          {/* Calendar Grid */}
          {days.map((day, dayIndex) => {
            const dayEvents = getEventsForDay(day);
            return (
              <div 
                key={dayIndex} 
                className="bg-gray-800 rounded-lg border border-gray-700 min-h-[200px]"
              >
                <div className="p-2">
                  {dayEvents.map(event => (
                    <div key={event.id}>
                      {expandedEvent && expandedEvent.id === event.id ? (
                        // Expanded view of the event - takes full width of the day cell
                        <div className={`${event.color} text-white rounded-lg transition-all duration-300 ease-in-out mb-2`}>
                          <div className="p-4">
                            <div className="flex justify-between items-start">
                              <h4 className="font-bold text-xl">{event.title}</h4>
                              <button 
                                onClick={() => setExpandedEvent(null)}
                                className="p-1 rounded-full hover:bg-black hover:bg-opacity-20 transition"
                              >
                                <FiX size={20} />
                              </button>
                            </div>
                            
                            <div className="mt-4 space-y-3">
                              <div className="flex items-center">
                                <FiClock size={18} className="mr-3" />
                                <span className="text-lg">{format(parseISO(event.start), 'h:mm a')} - {format(parseISO(event.end), 'h:mm a')}</span>
                              </div>
                              <div className="flex items-center">
                                <FiMapPin size={18} className="mr-3" />
                                <span className="text-lg">{event.room}</span>
                              </div>
                              <div className="flex items-start">
                                <FiUsers size={18} className="mr-3 mt-1" />
                                <div>
                                  <div className="text-lg font-medium">Participants</div>
                                  <div className="mt-1">{event.participants.join(', ')}</div>
                                </div>
                              </div>
                            </div>
                            
                            <div className="flex space-x-3 mt-6">
                              <button
                                onClick={() => handleTakeAttendance(event)}
                                className="flex-1 flex items-center justify-center space-x-2 py-3 bg-white bg-opacity-25 hover:bg-opacity-35 rounded-xl transition-all duration-200"
                              >
                                <FiClipboard size={20} />
                                <span className="text-lg font-medium">Take Attendance</span>
                              </button>
                              <button
                                onClick={() => handleStartRecording(event)}
                                className="flex-1 flex items-center justify-center space-x-2 py-3 bg-white bg-opacity-25 hover:bg-opacity-35 rounded-xl transition-all duration-200"
                              >
                                <FiFileText size={20} />
                                <span className="text-lg font-medium">Start Recording</span>
                              </button>
                            </div>
                          </div>
                        </div>
                      ) : (
                        // Regular compact view of the event
                        <div 
                          className={`relative p-3 rounded-lg mb-2 cursor-pointer transition-all duration-200 ${event.color} text-white hover:opacity-90`}
                          onClick={() => handleEventClick(event)}
                        >
                          <div className="flex justify-between items-start">
                            <div>
                              <h4 className="font-medium truncate">{event.title}</h4>
                              <div className="flex items-center text-sm mt-1 opacity-90">
                                <FiClock size={12} className="mr-1" />
                                <span>{format(parseISO(event.start), 'h:mm a')}</span>
                              </div>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                  
                  {dayEvents.length === 0 && (
                    <div className="text-center py-4 text-gray-500 text-sm">
                      <p>No meetings</p>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Floating Action Button */}
      <button 
        onClick={handleCreateMeeting}
        className="fixed bottom-6 left-6 w-14 h-14 bg-blue-600 rounded-full flex items-center justify-center shadow-lg hover:bg-blue-700 transition transform hover:scale-105"
      >
        <FiPlus size={24} />
      </button>

      {/* New Meeting Modal */}
      {isModalOpen && (
        <NewMeetingModal 
          event={selectedEvent}
          onClose={() => setIsModalOpen(false)}
          onSave={handleSaveMeeting}
        />
      )}
    </div>
  );
};

export default CalendarDashboard;