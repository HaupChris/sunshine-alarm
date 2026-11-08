package de.hauptmann.sunshinealarm.api

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Next time an alarm rings after [now]. dayMask 0 means "once": the next
 * occurrence of the time, today or tomorrow.
 */
fun nextOccurrence(hour: Int, minute: Int, dayMask: Int, now: LocalDateTime): LocalDateTime? {
    val time = LocalTime.of(hour, minute)
    for (offset in 0L..7L) {
        val date = now.toLocalDate().plusDays(offset)
        val candidate = date.atTime(time)
        if (!candidate.isAfter(now)) continue
        if (dayMask == 0) return candidate
        val bit = Weekday.entries[date.dayOfWeek.value - 1].bit
        if (dayMask and bit != 0) return candidate
    }
    return null
}
