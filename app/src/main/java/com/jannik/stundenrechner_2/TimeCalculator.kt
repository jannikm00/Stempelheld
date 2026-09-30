package com.jannik.stundenrechner_2

import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Formatter to display time as HH:mm
val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Logic to add specific duration to a start time.
 */
fun calculateNewTime(startTime: LocalTime, hours: Long, minutes: Long = 0): LocalTime {
    return startTime.plusHours(hours).plusMinutes(minutes)
}