import React, { useState } from 'react';

const ViewToggle = ({ view, setView }) => {
  const [isOpen, setIsOpen] = useState(false);
  
  const views = [
    { id: 'month', label: 'Month-wise' }
  ];

  const getCurrentViewLabel = () => {
    const currentView = views.find(v => v.id === view);
    return currentView ? currentView.label : 'Month-wise';
  };

  const handleViewChange = (viewId) => {
    setView(viewId);
    setIsOpen(false);
  };

  return (
    <div className="relative">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center space-x-2 px-3 py-1 bg-gray-700 text-gray-300 hover:text-white rounded-lg transition"
      >
        <span>{getCurrentViewLabel()}</span>
        <svg 
          className={`w-4 h-4 transition-transform ${isOpen ? 'rotate-180' : ''}`} 
          fill="none" 
          stroke="currentColor" 
          viewBox="0 0 24 24"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-2 w-48 bg-gray-800 border border-gray-700 rounded-lg shadow-lg z-10">
          {views.map((v) => (
            <button
              key={v.id}
              onClick={() => handleViewChange(v.id)}
              className={`block w-full text-left px-4 py-2 text-sm transition ${
                view === v.id 
                  ? 'bg-blue-600 text-white' 
                  : 'text-gray-300 hover:bg-gray-700 hover:text-white'
              }`}
            >
              {v.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export default ViewToggle;