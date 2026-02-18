import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { 
  format, 
  startOfWeek, 
  endOfWeek, 
  startOfMonth, 
  endOfMonth, 
  eachDayOfInterval, 
  isSameDay, 
  addDays, 
  subDays, 
  addWeeks, 
  subWeeks, 
  addMonths, 
  subMonths, 
  parseISO, 
  isWithinInterval, 
  addMinutes,
  isValid
} from 'date-fns';
import { FiChevronLeft, FiChevronRight, FiPlus, FiCalendar, FiClipboard, FiFileText, FiX, FiClock, FiMapPin, FiUsers, FiMenu, FiDownload, FiHome, FiUser } from 'react-icons/fi';
import NewMeetingModal from './NewMeetingModal';
import usePageSync from '../../hooks/usePageSync';

const CalendarDashboard = () => {
  const navigate = useNavigate();
  const [currentDate, setCurrentDate] = useState(new Date());
  const [events, setEvents] = useState([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [expandedEvent, setExpandedEvent] = useState(null);
  const [selectedEvent, setSelectedEvent] = useState(null);
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [conflictError, setConflictError] = useState(null);
  
  // Refs for interval timers and component mount status
  const syncIntervalRef = useRef(null);
  const refreshIntervalRef = useRef(null);
  const isMountedRef = useRef(false);

  // Use the new page sync hook for automatic database synchronization
  const { performSync } = usePageSync({
    syncUser: true,
    syncFaculty: true,
    onSyncComplete: (results) => {
      console.log('Page sync completed successfully', results);
      // Update events with fresh data if needed
      if (results.facultyData) {
        // Here you could update the UI with fresh faculty data
      }
    },
    onSyncError: (error) => {
      console.error('Page sync failed', error);
    }
  });

  // Mock data for meetings with better validation
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

  // Function to check if there's a meeting within the next 2 minutes
  const checkUpcomingMeetings = () => {
    try {
      const now = new Date();
      const twoMinutesFromNow = addMinutes(now, 2);
      
      return events.some(event => {
        try {
          if (!event || !event.start) {
            console.warn('Event missing start time:', event);
            return false;
          }
          
          const eventStart = parseISO(event.start);
          // Check if the parsed date is valid
          if (!isValid(eventStart)) {
            console.warn('Invalid event start time:', event.start);
            return false;
          }
          
          return isWithinInterval(eventStart, { start: now, end: twoMinutesFromNow });
        } catch (error) {
          console.error('Error parsing event start time for upcoming meetings check:', event?.start, error);
          return false;
        }
      });
    } catch (error) {
      console.error('Error in checkUpcomingMeetings:', error);
      return false;
    }
  };

  // Function to set up periodic syncing
  const setupPeriodicSync = () => {
    try {
      console.log('Setting up periodic sync');
      
      // Clear any existing intervals
      if (syncIntervalRef.current) {
        console.log('Clearing existing sync interval');
        clearInterval(syncIntervalRef.current);
      }
      if (refreshIntervalRef.current) {
        console.log('Clearing existing refresh interval');
        clearInterval(refreshIntervalRef.current);
      }
      
      // Set up 2-minute sync interval
      syncIntervalRef.current = setInterval(async () => {
        try {
          console.log('Running periodic sync');
          
          // Check if component is still mounted before syncing
          if (!isMountedRef.current) {
            console.log('Component not mounted, clearing sync interval');
            if (syncIntervalRef.current) clearInterval(syncIntervalRef.current);
            return;
          }
          
          await performSync();
          console.log('Dashboard synced with database');
          
          // Check if there's a meeting within the next 2 minutes
          if (checkUpcomingMeetings()) {
            console.log('Upcoming meeting detected, setting up refresh interval');
            
            // Set up 15-second refresh interval
            refreshIntervalRef.current = setInterval(() => {
              console.log('Refreshing due to upcoming meeting');
              
              // Check if component is still mounted before refreshing
              if (!isMountedRef.current) {
                console.log('Component not mounted, clearing refresh interval');
                if (refreshIntervalRef.current) clearInterval(refreshIntervalRef.current);
                return;
              }
              
              window.location.reload();
            }, 15000); // 15 seconds
            
            // Clear the 2-minute sync interval since we're now refreshing every 15 seconds
            if (syncIntervalRef.current) {
              console.log('Clearing sync interval due to refresh interval setup');
              clearInterval(syncIntervalRef.current);
              syncIntervalRef.current = null;
            }
          }
        } catch (error) {
          console.error('Periodic sync failed:', error);
        }
      }, 120000); // 2 minutes
      
      console.log('Periodic sync setup complete');
    } catch (error) {
      console.error('Error setting up periodic sync:', error);
    }
  };

  // Function to check if a time slot is available for all participants
  const checkTimeSlotAvailability = (startTime, endTime, participants, excludedEventId = null, currentEvents = events) => {
    try {
      console.log('Checking time slot availability:', { startTime, endTime, participants, excludedEventId });
      
      // Validate inputs
      if (!startTime || !endTime) {
        console.warn('Missing start or end time for availability check');
        return { isAvailable: false, conflicts: [{ error: 'Missing time values' }] };
      }
      
      // Convert string dates to Date objects
      let startDateTime, endDateTime;
      
      if (typeof startTime === 'string') {
        startDateTime = parseISO(startTime);
        if (!isValid(startDateTime)) {
          console.warn('Invalid start time:', startTime);
          return { isAvailable: false, conflicts: [{ error: 'Invalid start time' }] };
        }
      } else {
        startDateTime = startTime;
      }
      
      if (typeof endTime === 'string') {
        endDateTime = parseISO(endTime);
        if (!isValid(endDateTime)) {
          console.warn('Invalid end time:', endTime);
          return { isAvailable: false, conflicts: [{ error: 'Invalid end time' }] };
        }
      } else {
        endDateTime = endTime;
      }
      
      // Check each participant for conflicts
      const conflicts = [];
      
      // Filter events to exclude the current event if editing
      const relevantEvents = excludedEventId 
        ? currentEvents.filter(event => {
            // Ensure we're comparing the same types
            const eventId = typeof event.id === 'string' ? parseInt(event.id, 10) : event.id;
            const excludedId = typeof excludedEventId === 'string' ? parseInt(excludedEventId, 10) : excludedEventId;
            return eventId !== excludedId;
          })
        : currentEvents;
      
      console.log('Relevant events for conflict checking:', relevantEvents);
      
      // For each participant, check if they have conflicting meetings
      (participants || []).forEach(participant => {
        // Find events where this participant is involved
        const participantEvents = relevantEvents.filter(event => 
          event.participants && event.participants.includes(participant)
        );
        
        console.log(`Events for participant ${participant}:`, participantEvents);
        
        // Check for time conflicts
        const conflict = participantEvents.find(event => {
          try {
            if (!event.start || !event.end) {
              console.warn('Event missing start or end time:', event);
              return false;
            }
            
            const eventStart = parseISO(event.start);
            const eventEnd = parseISO(event.end);
            
            // Check if the parsed dates are valid
            if (!isValid(eventStart) || !isValid(eventEnd)) {
              console.warn('Invalid event dates:', event.start, event.end);
              return false;
            }
            
            // Check if the new meeting overlaps with existing meeting
            return (
              (startDateTime >= eventStart && startDateTime < eventEnd) || // New start is during existing event
              (endDateTime > eventStart && endDateTime <= eventEnd) ||     // New end is during existing event
              (startDateTime <= eventStart && endDateTime >= eventEnd)     // New event completely encompasses existing
            );
          } catch (parseError) {
            console.error('Error parsing event dates:', parseError);
            return false; // If we can't parse the dates, we assume no conflict
          }
        });
        
        if (conflict) {
          conflicts.push({
            participant,
            conflictingEvent: conflict
          });
        }
      });
      
      console.log('Time slot availability check result:', { isAvailable: conflicts.length === 0, conflicts });
      
      return {
        isAvailable: conflicts.length === 0,
        conflicts
      };
    } catch (error) {
      console.error('Error checking time slot availability:', error);
      // If there's an error in our checking logic, we err on the side of caution and say it's not available
      return {
        isAvailable: false,
        conflicts: [{ error: 'Error checking availability: ' + error.message }]
      };
    }
  };

  // Function to check for new meetings before scheduling
  const checkForNewConflicts = async (meetingData, isUpdate, originalEventId) => {
    try {
      console.log('Checking for new conflicts:', { meetingData, isUpdate, originalEventId });
      
      // Check if component is still mounted before syncing
      if (!isMountedRef.current) {
        console.log('Component not mounted, skipping conflict check');
        return { isAvailable: true, conflicts: [], latestEvents: events };
      }
      
      // Perform a sync to get the latest events
      console.log('Performing sync for conflict check');
      await performSync();
      
      // Get the latest events after sync
      // In a real app, this would come from the sync results
      // For now, we'll just use the current events state
      const latestEvents = [...events];
      
      console.log('Latest events for conflict check:', latestEvents);
      
      // Check if the time slot is still available with the latest events
      const { isAvailable, conflicts } = checkTimeSlotAvailability(
        meetingData.start,
        meetingData.end,
        meetingData.participants,
        isUpdate ? originalEventId : null,
        latestEvents
      );
      
      console.log('Conflict check result:', { isAvailable, conflicts });
      return { isAvailable, conflicts, latestEvents };
    } catch (error) {
      console.error('Error checking for new conflicts:', error);
      // If sync fails, proceed with current data
      return { isAvailable: true, conflicts: [], latestEvents: events };
    }
  };

  useEffect(() => {
    console.log('CalendarDashboard mounting');
    
    // Set mounted ref to true when component mounts
    isMountedRef.current = true;
    
    try {
      console.log('Initializing calendar dashboard');
      
      // Test date-fns functions
      try {
        const testDate = new Date();
        const formatted = format(testDate, 'yyyy-MM-dd');
        console.log('Date-fns test successful:', formatted);
        
        // Test parseISO
        const testISO = '2025-04-19T10:00:00';
        const parsed = parseISO(testISO);
        console.log('parseISO test successful:', parsed);
      } catch (dateFnsError) {
        console.error('Date-fns test failed:', dateFnsError);
      }
      
      // In a real app, this would fetch from an API
      console.log('Loading mock meetings:', mockMeetings);
      setEvents(mockMeetings);
      
      // Set up periodic syncing when component mounts
      console.log('Setting up periodic sync');
      setupPeriodicSync();
    } catch (error) {
      console.error('Error initializing calendar dashboard:', error);
    }
    
    // Clean up intervals and mounted ref when component unmounts
    return () => {
      console.log('CalendarDashboard unmounting');
      isMountedRef.current = false;
      
      if (syncIntervalRef.current) {
        console.log('Clearing sync interval on unmount');
        clearInterval(syncIntervalRef.current);
      }
      if (refreshIntervalRef.current) {
        console.log('Clearing refresh interval on unmount');
        clearInterval(refreshIntervalRef.current);
      }
    };
  }, []);

  const navigateDate = (direction) => {
    console.log('Navigating date:', direction);
    setCurrentDate(direction === 'next' ? addMonths(currentDate, 1) : subMonths(currentDate, 1));
  };

  const goToToday = () => {
    console.log('Going to today');
    setCurrentDate(new Date());
  };

  const getDaysToDisplay = () => {
    const start = startOfMonth(currentDate);
    const end = endOfMonth(currentDate);
    return eachDayOfInterval({ start, end });
  };

  const getEventsForDay = (day) => {
    return events.filter(event => {
      try {
        if (!event || !event.start) {
          console.warn('Event missing start time:', event);
          return false;
        }
        
        const eventStart = parseISO(event.start);
        // Check if the parsed date is valid
        if (!isValid(eventStart)) {
          console.warn('Invalid event start time:', event.start);
          return false;
        }
        
        return isSameDay(eventStart, day);
      } catch (error) {
        console.error('Error parsing event start time:', event?.start, error);
        return false;
      }
    });
  };

  const handleEventClick = (event) => {
    try {
      console.log('Event clicked:', event);
      
      // Validate event
      if (!event) {
        console.warn('No event provided for click handler');
        return;
      }
      
      // Toggle expansion of the clicked event
      if (expandedEvent && expandedEvent.id === event.id) {
        console.log('Collapsing event');
        setExpandedEvent(null); // Collapse if already expanded
      } else {
        console.log('Expanding event');
        setExpandedEvent(event); // Expand the clicked event
      }
    } catch (error) {
      console.error('Error handling event click:', error);
      // Show user-friendly error message
      alert('Failed to handle event click. Please try again.');
    }
  };

  const handleCreateMeeting = () => {
    console.log('Creating new meeting');
    setSelectedEvent(null);
    setConflictError(null); // Clear any previous conflict errors
    setIsModalOpen(true);
  };

  const handleEditMeeting = React.useCallback((event) => {
    try {
      console.log('=== HANDLE EDIT MEETING START ===');
      console.log('Received event for editing:', event);
      
      // Check if event is valid
      if (!event) {
        console.error('No event provided for editing');
        alert('Invalid event data for editing');
        return;
      }
      
      // Validate required properties
      if (event.id === undefined || event.id === null) {
        console.error('Event missing ID:', event);
        alert('Event data is missing required ID');
        return;
      }
      
      if (!event.start) {
        console.error('Event missing start time:', event);
        alert('Event data is missing start time');
        return;
      }
      
      if (!event.end) {
        console.error('Event missing end time:', event);
        alert('Event data is missing end time');
        return;
      }
      
      // Create a safe clone of the event with only the necessary properties
      // Handle participants more carefully
      let safeParticipants = [];
      if (event.participants) {
        if (Array.isArray(event.participants)) {
          safeParticipants = [...event.participants];
        } else if (typeof event.participants === 'string') {
          safeParticipants = [event.participants];
        } else {
          safeParticipants = [];
        }
      }
      
      const eventClone = {
        id: event.id,
        title: event.title || '',
        start: event.start,
        end: event.end,
        participants: safeParticipants,
        room: event.room || '',
        color: event.color || 'bg-blue-500'
      };
      
      // Log specific event properties
      console.log('Event ID:', eventClone.id);
      console.log('Event Title:', eventClone.title);
      console.log('Event Start:', eventClone.start);
      console.log('Event End:', eventClone.end);
      console.log('Event Participants:', eventClone.participants);
      console.log('Event Room:', eventClone.room);
      console.log('Event Color:', eventClone.color);
      
      // Validate date formats
      const startValid = isValid(parseISO(eventClone.start));
      const endValid = isValid(parseISO(eventClone.end));
      
      if (!startValid) {
        console.error('Invalid start time format:', eventClone.start);
        alert('Event has invalid start time format');
        return;
      }
      
      if (!endValid) {
        console.error('Invalid end time format:', eventClone.end);
        alert('Event has invalid end time format');
        return;
      }
      
      // Properly set the selected event for editing
      console.log('Setting selected event state');
      setSelectedEvent(eventClone);
      setConflictError(null); // Clear any previous conflict errors
      setIsModalOpen(true);
      console.log('=== HANDLE EDIT MEETING END ===');
    } catch (error) {
      console.error('=== ERROR IN HANDLE EDIT MEETING ===');
      console.error('Error details:', error);
      console.error('Error stack:', error.stack);
      // Show user-friendly error message
      alert('Failed to open edit meeting dialog. Please try again. Error: ' + error.message);
    }
  }, []);

  const handleSaveMeeting = async (meetingData) => {
    try {
      console.log('Saving meeting data:', meetingData);
      console.log('Selected event:', selectedEvent);
      
      // Check if component is still mounted before proceeding
      if (!isMountedRef.current) {
        console.log('Component not mounted, skipping save');
        return;
      }
      
      // Validate meeting data
      if (!meetingData || !meetingData.title || !meetingData.start || !meetingData.end) {
        console.error('Invalid meeting data:', meetingData);
        alert('Meeting data is incomplete');
        return;
      }
      
      // Check if this is an update or new meeting
      const isUpdate = selectedEvent !== null;
      console.log('Is update:', isUpdate);
      
      // Check for new conflicts before scheduling
      console.log('Checking for conflicts');
      const { isAvailable, conflicts, latestEvents } = await checkForNewConflicts(
        meetingData,
        isUpdate,
        isUpdate ? selectedEvent.id : null
      );
      
      // Check if component is still mounted after async operation
      if (!isMountedRef.current) {
        console.log('Component not mounted after conflict check, skipping save');
        return;
      }
      
      if (!isAvailable) {
        // Handle conflicts - show error in the modal
        console.warn('Time slot conflict detected:', conflicts);
        const conflictMessage = `Time slot conflict detected for: ${conflicts.map(c => c.participant).join(', ')}`;
        setConflictError(conflictMessage);
        // Update events with latest data
        setEvents(latestEvents);
        return; // Don't save the meeting
      }
      
      // Validate time slot availability with latest events
      console.log('Performing final availability check');
      const { isAvailable: stillAvailable, conflicts: newConflicts } = checkTimeSlotAvailability(
        meetingData.start,
        meetingData.end,
        meetingData.participants,
        isUpdate ? selectedEvent.id : null,
        latestEvents
      );
      
      // Check if component is still mounted after sync
      if (!isMountedRef.current) {
        console.log('Component not mounted after availability check, skipping save');
        return;
      }
      
      if (!stillAvailable) {
        // Handle conflicts - show error in the modal
        console.warn('Time slot conflict detected after sync:', newConflicts);
        const conflictMessage = `Time slot conflict detected for: ${newConflicts.map(c => c.participant).join(', ')}`;
        setConflictError(conflictMessage);
        // Update events with latest data
        setEvents(latestEvents);
        return; // Don't save the meeting
      }
      
      if (isUpdate) {
        // Update existing event
        console.log('Updating existing event with ID:', selectedEvent.id);
        const updatedEvents = latestEvents.map(event => {
          // Handle ID comparison more carefully
          const eventId = typeof event.id === 'string' ? parseInt(event.id, 10) : event.id;
          const selectedEventId = typeof selectedEvent.id === 'string' ? parseInt(selectedEvent.id, 10) : selectedEvent.id;
          
          if (eventId === selectedEventId) {
            console.log('Updating event:', event, 'with data:', meetingData);
            return { ...event, ...meetingData };
          }
          return event;
        });
        setEvents(updatedEvents);
      } else {
        // Create new event
        const newEvent = {
          id: latestEvents.length + 1,
          ...meetingData,
          color: "bg-indigo-500"
        };
        console.log('Creating new event:', newEvent);
        setEvents([...latestEvents, newEvent]);
      }
      setIsModalOpen(false);
    } catch (error) {
      console.error('Error saving meeting:', error);
      
      // Check if component is still mounted before showing alert
      if (isMountedRef.current) {
        alert('An error occurred while saving the meeting. Please try again.');
        // Close the modal even if there's an error to prevent the app from hanging
        setIsModalOpen(false);
      }
    }
  };

  const handleTakeAttendance = (event) => {
    // Handle take attendance functionality
    console.log(`Taking attendance for ${event?.title || 'unknown event'}`);
    // TODO: Implement take attendance functionality
  };

  const handleStartRecording = (event) => {
    // Handle start recording functionality
    console.log(`Starting recording for ${event?.title || 'unknown event'}`);
    // TODO: Implement start recording functionality
  };

  const handleDownloadCSV = () => {
    console.log('Downloading faculty members CSV');
    
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
    console.log('Downloading calendar events CSV');
    
    // Create CSV content for events
    const headers = ['Title', 'Start Time', 'End Time', 'Room', 'Participants'];
    const rows = events.map(event => [
      event.title,
      event.start,
      event.end,
      event.room,
      event.participants ? (Array.isArray(event.participants) ? event.participants.join('; ') : String(event.participants)) : ''
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
    <div className="flex h-screen bg-gray-900 text-white">
      {/* Sidebar */}
      <div className={`fixed md:relative z-30 h-screen bg-gray-800 w-64 transform transition-transform duration-300 ease-in-out ${isSidebarOpen ? 'translate-x-0' : '-translate-x-full'} md:translate-x-0`}>
        <div className="p-4 border-b border-gray-700">
          <h1 className="text-xl font-bold">Meeting Scheduler</h1>
        </div>
        <nav className="p-4">
          <ul className="space-y-2">
            <li>
              <button
                onClick={() => {
                  console.log('Navigating to dashboard');
                  navigate('/');
                  setIsSidebarOpen(false);
                }}
                className="flex items-center w-full p-3 rounded-lg hover:bg-gray-700 transition"
                type="button"
              >
                <FiHome className="mr-3" size={18} />
                <span>Dashboard</span>
              </button>
            </li>
            <li>
              <button
                onClick={() => {
                  console.log('Navigating to calendar');
                  navigate('/');
                  setIsSidebarOpen(false);
                }}
                className="flex items-center w-full p-3 rounded-lg hover:bg-gray-700 transition"
                type="button"
              >
                <FiCalendar className="mr-3" size={18} />
                <span>Calendar</span>
              </button>
            </li>
            <li>
              <button
                onClick={() => {
                  console.log('Navigating to faculty members');
                  navigate('/faculty-members');
                  setIsSidebarOpen(false);
                }}
                className="flex items-center w-full p-3 rounded-lg hover:bg-gray-700 transition"
                type="button"
              >
                <FiUsers className="mr-3" size={18} />
                <span>Faculty Members</span>
              </button>
            </li>
            <li>
              <button
                onClick={handleDownloadCSV}
                className="flex items-center w-full p-3 rounded-lg hover:bg-gray-700 transition"
                type="button"
              >
                <FiDownload className="mr-3" size={18} />
                <span>Download CSV file</span>
              </button>
            </li>
            {/* Add Faculty menu item placed according to user preference */}
            <li>
              <button
                onClick={() => {
                  console.log('Navigating to add faculty');
                  navigate('/add-faculty');
                  setIsSidebarOpen(false);
                }}
                className="flex items-center w-full p-3 rounded-lg hover:bg-gray-700 transition"
                type="button"
              >
                <FiPlus className="mr-3" size={18} />
                <span>Add Faculty</span>
              </button>
            </li>
            <li>
              <button
                onClick={() => {
                  console.log('Navigating to profile');
                  navigate('/profile');
                  setIsSidebarOpen(false);
                }}
                className="flex items-center w-full p-3 rounded-lg hover:bg-gray-700 transition"
                type="button"
              >
                <FiUser className="mr-3" size={18} />
                <span>Profile</span>
              </button>
            </li>
          </ul>
        </nav>
      </div>

      {/* Main Content */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top Navigation Bar */}
        <div className="flex items-center justify-between p-4 bg-gray-800 border-b border-gray-700">
          <div className="flex items-center space-x-4">
            <button 
              onClick={() => {
                console.log('Toggling sidebar');
                setIsSidebarOpen(!isSidebarOpen);
              }}
              className="p-2 rounded-full hover:bg-gray-700 transition md:hidden"
              type="button"
            >
              <FiMenu size={20} />
            </button>
            <h1 className="text-xl font-bold">Meeting Scheduler</h1>
            <div className="flex items-center space-x-2">
              <button 
                onClick={goToToday}
                className="px-3 py-1 text-sm bg-gray-700 rounded hover:bg-gray-600 transition"
                type="button"
              >
                Today
              </button>
              <button 
                onClick={() => navigateDate('prev')}
                className="p-2 rounded-full hover:bg-gray-700 transition"
                type="button"
              >
                <FiChevronLeft size={20} />
              </button>
              <button 
                onClick={() => navigateDate('next')}
                className="p-2 rounded-full hover:bg-gray-700 transition"
                type="button"
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
              type="button"
            >
              <FiDownload size={18} />
              <span>Download CSV</span>
            </button>
            
            <button 
              className="flex items-center space-x-2 px-4 py-2 bg-blue-600 rounded-lg hover:bg-blue-700 transition"
              type="button"
            >
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
              try {
                const dayEvents = getEventsForDay(day);
                return (
                  <div 
                    key={dayIndex} 
                    className="bg-gray-800 rounded-lg border border-gray-700 min-h-[200px]"
                  >
                    <div className="p-2">
                      {dayEvents.map(event => {
                        try {
                          // Validate event before rendering
                          if (!event || event.id === undefined) {
                            console.warn('Invalid event data:', event);
                            return (
                              <div key="invalid-event" className="p-3 rounded-lg mb-2 bg-red-500 text-white">
                                Invalid event data
                              </div>
                            );
                          }
                          
                          return (
                            <div key={event.id}>
                              {expandedEvent && expandedEvent.id === event.id ? (
                                // Expanded view of the event - takes full width of the day cell
                                <div className={`${event.color} text-white rounded-lg transition-all duration-300 ease-in-out mb-2`}>
                                  <div className="p-4">
                                    <div className="flex justify-between items-start">
                                      <h4 className="font-bold text-xl">{event.title}</h4>
                                      <button 
                                        onClick={() => {
                                          console.log('Closing expanded event');
                                          setExpandedEvent(null);
                                        }}
                                        className="p-1 rounded-full hover:bg-black hover:bg-opacity-20 transition"
                                        type="button"
                                      >
                                        <FiX size={20} />
                                      </button>
                                    </div>
                                    
                                    <div className="mt-4 space-y-3">
                                      <div className="flex items-center">
                                        <FiClock size={18} className="mr-3" />
                                        <span className="text-lg">
                                          {(() => {
                                            try {
                                              if (!event.start || !event.end) {
                                                return 'Invalid time range';
                                              }
                                              
                                              const start = parseISO(event.start);
                                              const end = parseISO(event.end);
                                              
                                              if (!isValid(start) || !isValid(end)) {
                                                return 'Invalid time range';
                                              }
                                              
                                              return `${format(start, 'h:mm a')} - ${format(end, 'h:mm a')}`;
                                            } catch (error) {
                                              console.error('Error formatting time range:', event.start, event.end, error);
                                              return 'Invalid time range';
                                            }
                                          })()}
                                        </span>
                                      </div>
                                      <div className="flex items-center">
                                        <FiMapPin size={18} className="mr-3" />
                                        <span className="text-lg">{event.room || 'No room specified'}</span>
                                      </div>
                                      <div className="flex items-start">
                                        <FiUsers size={18} className="mr-3 mt-1" />
                                        <div>
                                          <div className="text-lg font-medium">Participants</div>
                                          <div className="mt-1">
                                            {(() => {
                                              try {
                                                if (!event.participants) {
                                                  return 'No participants';
                                                }
                                                
                                                if (Array.isArray(event.participants)) {
                                                  return event.participants.length > 0 ? 
                                                    event.participants.join(', ') : 
                                                    'No participants';
                                                } else if (typeof event.participants === 'string') {
                                                  return event.participants || 'No participants';
                                                } else {
                                                  return String(event.participants) || 'No participants';
                                                }
                                              } catch (error) {
                                                console.error('Error displaying participants:', event.participants, error);
                                                return 'Error displaying participants';
                                              }
                                            })()}
                                          </div>
                                        </div>
                                      </div>
                                    </div>
                                    
                                    <div className="flex space-x-3 mt-6">
                                      <button
                                        onClick={() => handleTakeAttendance(event)}
                                        className="flex-1 flex items-center justify-center space-x-2 py-3 bg-white bg-opacity-25 hover:bg-opacity-35 rounded-xl transition-all duration-200"
                                        type="button"
                                      >
                                        <FiClipboard size={20} />
                                        <span className="text-lg font-medium">Take Attendance</span>
                                      </button>
                                      <button
                                        onClick={() => handleStartRecording(event)}
                                        className="flex-1 flex items-center justify-center space-x-2 py-3 bg-white bg-opacity-25 hover:bg-opacity-35 rounded-xl transition-all duration-200"
                                        type="button"
                                      >
                                        <FiFileText size={20} />
                                        <span className="text-lg font-medium">Start Recording</span>
                                      </button>
                                      {/* Edit Meeting Button */}
                                      <button
                                        onClick={() => {
                                          console.log('Edit button clicked for event:', event);
                                          // Add a small delay to prevent rapid clicks
                                          setTimeout(() => {
                                            handleEditMeeting(event);
                                          }, 100);
                                        }}
                                        className="flex-1 flex items-center justify-center space-x-2 py-3 bg-white bg-opacity-25 hover:bg-opacity-35 rounded-xl transition-all duration-200"
                                        type="button"
                                      >
                                        <FiCalendar size={20} />
                                        <span className="text-lg font-medium">Edit</span>
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
                                        <span>
                                          {(() => {
                                            try {
                                              if (!event.start) {
                                                return 'Invalid time';
                                              }
                                              
                                              const start = parseISO(event.start);
                                              if (!isValid(start)) {
                                                return 'Invalid time';
                                              }
                                              
                                              return format(start, 'h:mm a');
                                            } catch (error) {
                                              console.error('Error formatting time:', event.start, error);
                                              return 'Invalid time';
                                            }
                                          })()}
                                        </span>
                                      </div>
                                    </div>
                                  </div>
                                </div>
                              )}
                            </div>
                          );
                        } catch (eventRenderError) {
                          console.error('Error rendering event:', event, eventRenderError);
                          return (
                            <div key={event?.id || `error-${dayIndex}`} className="p-3 rounded-lg mb-2 bg-red-500 text-white">
                              Error displaying event
                            </div>
                          );
                        }
                      })}
                      
                      {dayEvents.length === 0 && (
                        <div className="text-center py-4 text-gray-500 text-sm">
                          <p>No meetings</p>
                        </div>
                      )}
                    </div>
                  </div>
                );
              } catch (dayRenderError) {
                console.error('Error rendering day:', day, dayRenderError);
                return (
                  <div key={dayIndex} className="bg-gray-800 rounded-lg border border-gray-700 min-h-[200px] p-2">
                    <div className="text-center py-4 text-red-500">
                      Error displaying day
                    </div>
                  </div>
                );
              }
            })}
          </div>
        </div>

        {/* Floating Action Button */}
        <button 
          onClick={handleCreateMeeting}
          className="fixed bottom-6 left-6 w-14 h-14 bg-blue-600 rounded-full flex items-center justify-center shadow-lg hover:bg-blue-700 transition transform hover:scale-105 md:left-80"
          type="button"
        >
          <FiPlus size={24} />
        </button>

        {/* New Meeting Modal */}
        {isModalOpen && (
          <div>
            {console.log('Rendering NewMeetingModal with selectedEvent:', selectedEvent)}
            <NewMeetingModal 
              event={selectedEvent}
              conflictError={conflictError}
              onClose={() => {
                console.log('Closing modal');
                setIsModalOpen(false);
                setConflictError(null);
              }}
              onSave={(meetingData) => {
                console.log('Saving meeting:', meetingData);
                handleSaveMeeting(meetingData);
              }}
            />
          </div>
        )}

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

export default CalendarDashboard;