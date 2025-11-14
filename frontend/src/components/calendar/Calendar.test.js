import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import CalendarDashboard from './CalendarDashboard';

describe('CalendarDashboard', () => {
  it('renders without crashing', () => {
    render(React.createElement(CalendarDashboard));
    expect(screen.getByText('Meeting Scheduler')).toBeInTheDocument();
  });
});