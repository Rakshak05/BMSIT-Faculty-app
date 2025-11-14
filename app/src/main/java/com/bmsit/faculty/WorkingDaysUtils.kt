package com.bmsit.faculty

import java.util.Calendar
import java.util.Date

/**
 * Utility class for calculating working days
 * 
 * Created by Rakshak S. Barkur
 */
object WorkingDaysUtils {
    
    /**
     * Calculate the number of working days between two dates
     * Working days are Monday through Friday (excluding weekends)
     * 
     * @param startDate The start date
     * @param endDate The end date
     * @return Number of working days between the dates
     */
    fun calculateWorkingDays(startDate: Date, endDate: Date): Int {
        val startCalendar = Calendar.getInstance().apply { time = startDate }
        val endCalendar = Calendar.getInstance().apply { time = endDate }
        
        // Ensure start date is before end date
        if (startCalendar.after(endCalendar)) {
            return 0
        }
        
        var workingDays = 0
        val current = startCalendar.clone() as Calendar
        
        // Iterate through each day
        while (!current.after(endCalendar)) {
            val dayOfWeek = current.get(Calendar.DAY_OF_WEEK)
            
            // Check if it's a weekday (Monday=2, Tuesday=3, ..., Friday=6)
            if (dayOfWeek in 2..6) {
                workingDays++
            }
            
            // Move to next day
            current.add(Calendar.DAY_OF_YEAR, 1)
        }
        
        return workingDays
    }
    
    /**
     * Check if a meeting can be edited based on the 3 working days rule
     * 
     * @param meetingEndTime The time when the meeting ended (attendance was taken)
     * @return true if the meeting can still be edited, false otherwise
     */
    fun canEditMeeting(meetingEndTime: Date): Boolean {
        val now = Calendar.getInstance().time
        val workingDays = calculateWorkingDays(meetingEndTime, now)
        return workingDays <= 3
    }
}