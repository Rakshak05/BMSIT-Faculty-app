package com.bmsit.faculty

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Draft meeting extracted from natural language
data class MeetingDraft(
    var title: String = "",
    var attendees: String = "All Faculty",
    var location: String = "Not specified",
    var dateTime: Timestamp = Timestamp.now()
)

interface VoiceNlu {
    fun parse(command: String, nowCal: Calendar = Calendar.getInstance()): MeetingDraft
}

class VoiceNluRuleBased : VoiceNlu {
    private val months = mapOf(
        "january" to Calendar.JANUARY, "jan" to Calendar.JANUARY,
        "february" to Calendar.FEBRUARY, "feb" to Calendar.FEBRUARY,
        "march" to Calendar.MARCH, "mar" to Calendar.MARCH,
        "april" to Calendar.APRIL, "apr" to Calendar.APRIL,
        "may" to Calendar.MAY,
        "june" to Calendar.JUNE, "jun" to Calendar.JUNE,
        "july" to Calendar.JULY, "jul" to Calendar.JULY,
        "august" to Calendar.AUGUST, "aug" to Calendar.AUGUST,
        "september" to Calendar.SEPTEMBER, "sep" to Calendar.SEPTEMBER, "sept" to Calendar.SEPTEMBER,
        "october" to Calendar.OCTOBER, "oct" to Calendar.OCTOBER,
        "november" to Calendar.NOVEMBER, "nov" to Calendar.NOVEMBER,
        "december" to Calendar.DECEMBER, "dec" to Calendar.DECEMBER
    )
    private val weekdays = mapOf(
        "sunday" to Calendar.SUNDAY,
        "monday" to Calendar.MONDAY,
        "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY,
        "saturday" to Calendar.SATURDAY
    )

    override fun parse(command: String, nowCal: Calendar): MeetingDraft {
        val lower = command.lowercase(Locale.getDefault())
        val cal = nowCal.clone() as Calendar

        // 1) Attendees
        val attendees = when {
            lower.contains("hods") || lower.contains("hod") || lower.contains("head of department") -> "All HODs"
            lower.contains("deans") || lower.contains("dean") -> "All Deans"
            else -> "All Faculty"
        }

        // 2) Date words
        when {
            lower.contains("day after tomorrow") -> cal.add(Calendar.DAY_OF_YEAR, 2)
            lower.contains("tomorrow") -> cal.add(Calendar.DAY_OF_YEAR, 1)
            lower.contains("today") -> {}
            else -> {
                // Next weekday name
                val w = weekdays.entries.firstOrNull { lower.contains(it.key) }
                if (w != null) moveToNextWeekday(cal, w.value)
                else parseExplicitDate(lower, cal)
            }
        }

        // 3) Time (parse before location to avoid grabbing 'at 2 pm' as a location)
        parseTime(lower, cal)

        // 4) Subject after 'about ...' (used for title if present)
        val aboutMatch = Regex("\\babout\\s+(.+)$", RegexOption.IGNORE_CASE).find(command)
        val subjectRaw = aboutMatch?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', '!', '?')
        val subjectClean = subjectRaw
            ?.replace(Regex("\\s+"), " ")
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        // 5) Location (ignore 'at <time>' patterns). Search only before 'about' segment if present.
        val searchSpan = if (aboutMatch != null) lower.substring(0, aboutMatch.range.first) else lower
        // Negative lookahead for time right after 'at '
        val atPattern = Regex("\\bat\\s+(?!\\d{1,2}(?::[0-5]\\d)?\\s*(am|pm)?\\b)([a-z0-9#'\\-\\.\\s]+)")
        val inPattern = Regex("\\bin\\s+([a-z0-9#'\\-\\.\\s]+)")
        var location = atPattern.find(searchSpan)?.groupValues?.getOrNull(1)
            ?: inPattern.find(searchSpan)?.groupValues?.getOrNull(1)
        location = location?.trim()?.replace(Regex("\\s+"), " ")
        if (location.isNullOrBlank() || location.matches(Regex("^\\d+$"))) {
            location = "Not specified"
        }

        // 6) Title
        val title = if (!subjectClean.isNullOrBlank()) {
            "Meeting: $subjectClean"
        } else when (attendees) {
            "All HODs" -> "Meeting with HODs"
            "All Deans" -> "Meeting with Deans"
            else -> "Faculty Meeting"
        }

        return MeetingDraft(
            title = title,
            attendees = attendees,
            location = location,
            dateTime = com.google.firebase.Timestamp(cal.time)
        )
    }

    private fun moveToNextWeekday(cal: Calendar, targetDow: Int) {
        val currentDow = cal.get(Calendar.DAY_OF_WEEK)
        var delta = (targetDow - currentDow + 7) % 7
        if (delta == 0) delta = 7 // next occurrence
        cal.add(Calendar.DAY_OF_YEAR, delta)
    }

    private fun parseExplicitDate(lower: String, cal: Calendar) {
        // 1) Numeric formats: 1/10[/2025] or 1-10[-2025]
        val m1 = Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b").find(lower)
        if (m1 != null) {
            val d = m1.groupValues[1].toInt()
            val mo = m1.groupValues[2].toInt()
            val yStr = m1.groupValues.getOrNull(3)
            val y = if (!yStr.isNullOrBlank()) normalizeYear(yStr.toInt()) else cal.get(Calendar.YEAR)
            cal.set(y, mo - 1, d)
            return
        }
        // 2) Month name formats: "1st October [2025]" or "October 1 [2025]"
        val ordinalDay = Regex("\\b(\\d{1,2})(st|nd|rd|th)?\\b")
        months.keys.forEach { key ->
            if (lower.contains(key)) {
                // try pattern: <day> <month>
                val dm = Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+${key}\\b").find(lower)
                val md = Regex("\\b${key}\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b").find(lower)
                val y = Regex("\\b(20\\d{2}|19\\d{2})\\b").find(lower)?.groupValues?.get(1)?.toInt()
                val day = dm?.groupValues?.get(1)?.toInt() ?: md?.groupValues?.get(1)?.toInt()
                val monthIdx = months[key]!!
                if (day != null) {
                    cal.set(y ?: cal.get(Calendar.YEAR), monthIdx, day)
                    return
                }
            }
        }
    }

    private fun parseTime(lower: String, cal: Calendar) {
        // Matches 16:30, 4:30 pm, 4pm, 09, 9 am
        val timeRegex = Regex("\\b(\\d{1,2})(?::([0-5]\\d))?\\s*(am|pm)?\\b")
        val match = timeRegex.find(lower)
        if (match != null) {
            var h = match.groupValues[1].toIntOrNull() ?: 9
            val m = match.groupValues[2].toIntOrNull() ?: 0
            val ampm = match.groupValues[3]
            if (ampm == "pm" && h in 1..11) h += 12
            if (ampm == "am" && h == 12) h = 0

            // Office hours heuristic (8:00–20:00)
            // Only apply when AM/PM is not specified to avoid overriding explicit user intent.
            if (ampm.isBlank()) {
                if (h in 1..7) {
                    // Likely PM meeting (e.g., 2 -> 14)
                    h += 12
                }
                if (h > 23) h = 23
                // Clamp to office hours just in case
                if (h < 8) h = 8
                if (h > 20) h = 20
            }

            cal.set(Calendar.HOUR_OF_DAY, h)
            cal.set(Calendar.MINUTE, m)
            cal.set(Calendar.SECOND, 0)
        } else {
            // default 9:00 (within office hours)
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
        }
    }

    private fun normalizeYear(y: Int): Int {
        return if (y < 100) 2000 + y else y
    }
}
