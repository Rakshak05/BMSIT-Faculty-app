import React, { useState, useEffect, useRef, useMemo } from 'react';
import { format, parseISO, isValid } from 'date-fns';
import { FiX, FiCalendar, FiClock, FiUsers, FiMapPin, FiAlertTriangle } from 'react-icons/fi';

const NewMeetingModal = ({ event, conflictError, onClose, onSave }) => {
  console.log('=== NEW MEETING MODAL RENDER START ===');
  console.log('Received props:', { event, conflictError });
  
  // Ref to track if component is mounted
  const isMountedRef = useRef(false);
  
  // Safely parse date values with extensive error handling
  const parseEventDate = (dateString, fallbackFormat) => {
    try {
      console.log('Parsing date:', dateString, 'with fallback:', fallbackFormat);
      if (!dateString) {
        console.log('No date string provided, returning fallback');
        // Return the fallback format directly if dateString is falsy
        return fallbackFormat;
      }
      
      // Try to parse the date
      const parsed = parseISO(dateString);
      console.log('Parsed date object:', parsed);
      
      // Check if the parsed date is valid
      if (!isValid(parsed)) {
        console.warn('Parsed date is invalid:', parsed);
        return fallbackFormat;
      }
      
      const formatted = format(parsed, fallbackFormat);
      console.log('Parsed date result:', formatted);
      return formatted;
    } catch (error) {
      console.error('Error parsing date:', dateString, error);
      // Return the fallback format if parsing fails
      return fallbackFormat;
    }
  };
  
  // Memoize initial values to prevent recalculation on every render
  const initialData = useMemo(() => {
    try {
      console.log('Calculating initial data for modal');
      const todayFormatted = format(new Date(), 'yyyy-MM-dd');
      const currentTimeFormatted = format(new Date(), 'HH:mm');
      
      // Handle case where event might be null or undefined
      const safeEvent = event || {};
      
      const initialTitle = safeEvent.title || '';
      const initialDate = safeEvent.start ? parseEventDate(safeEvent.start, 'yyyy-MM-dd') : todayFormatted;
      const initialStartTime = safeEvent.start ? parseEventDate(safeEvent.start, 'HH:mm') : currentTimeFormatted;
      const initialEndTime = safeEvent.end ? parseEventDate(safeEvent.end, 'HH:mm') : currentTimeFormatted;
      
      // Handle participants more safely
      let initialParticipants = '';
      if (safeEvent.participants) {
        if (Array.isArray(safeEvent.participants)) {
          initialParticipants = safeEvent.participants.join(', ');
        } else if (typeof safeEvent.participants === 'string') {
          initialParticipants = safeEvent.participants;
        } else {
          initialParticipants = String(safeEvent.participants);
        }
      }
      
      const initialRoom = safeEvent.room || '';
      
      console.log('Initial data calculated:', {
        title: initialTitle,
        date: initialDate,
        startTime: initialStartTime,
        endTime: initialEndTime,
        participants: initialParticipants,
        room: initialRoom
      });
      
      return {
        title: initialTitle,
        date: initialDate,
        startTime: initialStartTime,
        endTime: initialEndTime,
        participants: initialParticipants,
        room: initialRoom
      };
    } catch (error) {
      console.error('Error calculating initial data:', error);
      // Return safe fallback values
      const todayFormatted = format(new Date(), 'yyyy-MM-dd');
      const currentTimeFormatted = format(new Date(), 'HH:mm');
      
      return {
        title: '',
        date: todayFormatted,
        startTime: currentTimeFormatted,
        endTime: currentTimeFormatted,
        participants: '',
        room: ''
      };
    }
  }, [event]);
  
  const [title, setTitle] = useState(initialData.title);
  const [date, setDate] = useState(initialData.date);
  const [startTime, setStartTime] = useState(initialData.startTime);
  const [endTime, setEndTime] = useState(initialData.endTime);
  const [participants, setParticipants] = useState(initialData.participants);
  const [room, setRoom] = useState(initialData.room);
  
  // Set mounted ref to true when component mounts
  useEffect(() => {
    console.log('NewMeetingModal component mounting');
    isMountedRef.current = true;
    
    // Cleanup function to set mounted ref to false when component unmounts
    return () => {
      console.log('NewMeetingModal component unmounting');
      isMountedRef.current = false;
    };
  }, []);
  
  console.log('=== NEW MEETING MODAL RENDER END ===');

  const handleSubmit = (e) => {
    try {
      console.log('=== HANDLE SUBMIT START ===');
      e.preventDefault();
      
      // Check if component is still mounted before proceeding
      if (!isMountedRef.current) {
        console.log('Component not mounted, skipping submit');
        return;
      }
      
      console.log('Form data:', { title, date, startTime, endTime, participants, room });
      
      // Validate required fields
      if (!title.trim()) {
        console.warn('Title is required');
        alert('Please enter a meeting title');
        return;
      }
      
      // Validate date
      if (!date) {
        console.warn('Date is required');
        alert('Please select a date');
        return;
      }
      
      // Validate time
      if (!startTime || !endTime) {
        console.warn('Time is required');
        alert('Please select start and end times');
        return;
      }
      
      const startDateTime = new Date(`${date}T${startTime}:00`);
      const endDateTime = new Date(`${date}T${endTime}:00`);
      
      console.log('Start datetime:', startDateTime);
      console.log('End datetime:', endDateTime);
      
      // Check if dates are valid
      if (isNaN(startDateTime.getTime()) || isNaN(endDateTime.getTime())) {
        console.warn('Invalid date values');
        alert('Please enter valid date and time values');
        return;
      }
      
      if (startDateTime >= endDateTime) {
        console.warn('End time is not after start time');
        alert('End time must be after start time');
        return;
      }
      
      // Safely split participants
      let participantsArray = [];
      if (participants && typeof participants === 'string') {
        participantsArray = participants.split(',').map(p => p.trim()).filter(p => p);
      }
      
      const meetingData = {
        title,
        start: startDateTime.toISOString(),
        end: endDateTime.toISOString(),
        participants: participantsArray,
        room
      };
      
      console.log('Saving meeting data:', meetingData);
      // Pass the meeting data to the parent component for validation
      onSave(meetingData);
      console.log('=== HANDLE SUBMIT END ===');
    } catch (error) {
      console.error('=== ERROR IN HANDLE SUBMIT ===');
      console.error('Submit error:', error);
      console.error('Error stack:', error.stack);
      
      // Check if component is still mounted before showing alert
      if (isMountedRef.current) {
        alert('An error occurred while saving the meeting. Please try again. Error: ' + error.message);
      }
    }
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-gray-800 rounded-xl w-full max-w-md border border-gray-700">
        <div className="flex justify-between items-center p-4 border-b border-gray-700">
          <h3 className="text-lg font-bold">
            {event ? 'Edit Meeting' : 'New Meeting'}
          </h3>
          <button 
            onClick={() => {
              console.log('Close button clicked');
              onClose();
            }}
            className="p-2 rounded-full hover:bg-gray-700 transition"
            type="button"
          >
            <FiX size={20} />
          </button>
        </div>
        
        <form onSubmit={handleSubmit} className="p-4 space-y-4">
          {/* Conflict Error Message */}
          {conflictError && (
            <div className="p-3 bg-red-500/10 border border-red-500 rounded text-red-500 text-sm flex items-start">
              <FiAlertTriangle className="mr-2 mt-0.5 flex-shrink-0" />
              <div>
                <div className="font-medium">Time slot conflict detected!</div>
                <div className="mt-1">{conflictError}</div>
              </div>
            </div>
          )}
          
          <div>
            <label className="block text-sm font-medium mb-1">Title</label>
            <input
              type="text"
              value={title}
              onChange={(e) => {
                console.log('Title changed to:', e.target.value);
                setTitle(e.target.value);
              }}
              className="w-full bg-gray-700 border border-gray-600 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="Meeting title"
              required
            />
          </div>
          
          <div>
            <label className="block text-sm font-medium mb-1">Date</label>
            <div className="relative">
              <FiCalendar className="absolute left-3 top-3 text-gray-400" />
              <input
                type="date"
                value={date}
                onChange={(e) => {
                  console.log('Date changed to:', e.target.value);
                  setDate(e.target.value);
                }}
                className="w-full bg-gray-700 border border-gray-600 rounded-lg pl-10 pr-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                required
              />
            </div>
          </div>
          
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1">Start Time</label>
              <div className="relative">
                <FiClock className="absolute left-3 top-3 text-gray-400" />
                <input
                  type="time"
                  value={startTime}
                  onChange={(e) => {
                    console.log('Start time changed to:', e.target.value);
                    setStartTime(e.target.value);
                  }}
                  className="w-full bg-gray-700 border border-gray-600 rounded-lg pl-10 pr-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  required
                />
              </div>
            </div>
            
            <div>
              <label className="block text-sm font-medium mb-1">End Time</label>
              <div className="relative">
                <FiClock className="absolute left-3 top-3 text-gray-400" />
                <input
                  type="time"
                  value={endTime}
                  onChange={(e) => {
                    console.log('End time changed to:', e.target.value);
                    setEndTime(e.target.value);
                  }}
                  className="w-full bg-gray-700 border border-gray-600 rounded-lg pl-10 pr-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  required
                />
              </div>
            </div>
          </div>
          
          <div>
            <label className="block text-sm font-medium mb-1">Participants</label>
            <div className="relative">
              <FiUsers className="absolute left-3 top-3 text-gray-400" />
              <input
                type="text"
                value={participants}
                onChange={(e) => {
                  console.log('Participants changed to:', e.target.value);
                  setParticipants(e.target.value);
                }}
                className="w-full bg-gray-700 border border-gray-600 rounded-lg pl-10 pr-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="Dr. Smith, Prof. Johnson, ..."
              />
            </div>
          </div>
          
          <div>
            <label className="block text-sm font-medium mb-1">Room</label>
            <div className="relative">
              <FiMapPin className="absolute left-3 top-3 text-gray-400" />
              <input
                type="text"
                value={room}
                onChange={(e) => {
                  console.log('Room changed to:', e.target.value);
                  setRoom(e.target.value);
                }}
                className="w-full bg-gray-700 border border-gray-600 rounded-lg pl-10 pr-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="Room 101"
              />
            </div>
          </div>
          
          <div className="flex justify-end space-x-3 pt-4">
            <button
              type="button"
              onClick={() => {
                console.log('Cancel button clicked');
                onClose();
              }}
              className="px-4 py-2 border border-gray-600 rounded-lg hover:bg-gray-700 transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-4 py-2 bg-blue-600 rounded-lg hover:bg-blue-700 transition"
            >
              {event ? 'Update' : 'Create'} Meeting
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default NewMeetingModal;