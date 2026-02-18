import React, { useState } from 'react';
import { format, parseISO } from 'date-fns';
import { FiUsers, FiMapPin, FiClock } from 'react-icons/fi';

const EventBlock = ({ event, onClick }) => {
  const [isHovered, setIsHovered] = useState(false);

  const startTime = format(parseISO(event.start), 'h:mm a');
  const endTime = format(parseISO(event.end), 'h:mm a');

  return (
    <div 
      className={`relative p-3 rounded mb-2 cursor-pointer transition-all duration-200 ${event.color} text-white`}
      onClick={onClick}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
    >
      <div className="flex justify-between items-start">
        <div>
          <h4 className="font-medium truncate">{event.title}</h4>
          <div className="flex items-center text-sm mt-1 opacity-90">
            <FiClock size={12} className="mr-1" />
            <span>{startTime} - {endTime}</span>
          </div>
        </div>
      </div>

      {/* Tooltip on hover */}
      {isHovered && (
        <div className="absolute left-0 top-full mt-2 w-64 bg-gray-800 border border-gray-700 rounded-lg shadow-xl z-10 p-3">
          <h4 className="font-bold text-lg mb-2">{event.title}</h4>
          <div className="space-y-2 text-sm">
            <div className="flex items-start">
              <FiClock size={16} className="mr-2 mt-0.5 flex-shrink-0" />
              <div>
                <div>{format(parseISO(event.start), 'EEEE, MMMM d, yyyy')}</div>
                <div>{startTime} - {endTime}</div>
              </div>
            </div>
            <div className="flex items-start">
              <FiMapPin size={16} className="mr-2 mt-0.5 flex-shrink-0" />
              <div>{event.room}</div>
            </div>
            <div className="flex items-start">
              <FiUsers size={16} className="mr-2 mt-0.5 flex-shrink-0" />
              <div>
                {event.participants.join(', ')}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default EventBlock;