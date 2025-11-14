import React, { useState } from 'react';
import { format, parseISO } from 'date-fns';
import { FiX, FiCalendar, FiClock, FiUsers, FiMapPin } from 'react-icons/fi';

const NewMeetingModal = ({ event, onClose, onSave }) => {
  const [title, setTitle] = useState(event?.title || '');
  const [date, setDate] = useState(event?.start ? format(parseISO(event.start), 'yyyy-MM-dd') : format(new Date(), 'yyyy-MM-dd'));
  const [startTime, setStartTime] = useState(event?.start ? format(parseISO(event.start), 'HH:mm') : '09:00');
  const [endTime, setEndTime] = useState(event?.end ? format(parseISO(event.end), 'HH:mm') : '10:00');
  const [participants, setParticipants] = useState(event?.participants?.join(', ') || '');
  const [room, setRoom] = useState(event?.room || '');

  const handleSubmit = (e) => {
    e.preventDefault();
    
    const startDateTime = new Date(`${date}T${startTime}:00`);
    const endDateTime = new Date(`${date}T${endTime}:00`);
    
    if (startDateTime >= endDateTime) {
      alert('End time must be after start time');
      return;
    }
    
    const meetingData = {
      title,
      start: startDateTime.toISOString(),
      end: endDateTime.toISOString(),
      participants: participants.split(',').map(p => p.trim()).filter(p => p),
      room
    };
    
    onSave(meetingData);
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
      <div className="bg-gray-800 rounded-xl w-full max-w-md border border-gray-700">
        <div className="flex justify-between items-center p-4 border-b border-gray-700">
          <h3 className="text-lg font-bold">
            {event ? 'Edit Meeting' : 'New Meeting'}
          </h3>
          <button 
            onClick={onClose}
            className="p-2 rounded-full hover:bg-gray-700 transition"
          >
            <FiX size={20} />
          </button>
        </div>
        
        <form onSubmit={handleSubmit} className="p-4 space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1">Title</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
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
                onChange={(e) => setDate(e.target.value)}
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
                  onChange={(e) => setStartTime(e.target.value)}
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
                  onChange={(e) => setEndTime(e.target.value)}
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
                onChange={(e) => setParticipants(e.target.value)}
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
                onChange={(e) => setRoom(e.target.value)}
                className="w-full bg-gray-700 border border-gray-600 rounded-lg pl-10 pr-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="Room 101"
              />
            </div>
          </div>
          
          <div className="flex justify-end space-x-3 pt-4">
            <button
              type="button"
              onClick={onClose}
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