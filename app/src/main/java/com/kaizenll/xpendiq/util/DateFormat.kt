package com.kaizenll.xpendiq.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFormat {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /** Row line: "Today, 18:42" / "Yesterday, 09:15" / "23 May, 18:42" / "12 Jan 2025, 14:00". */
    fun row(epochMillis: Long): String {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
        val today = LocalDate.now(zone)
        val date = ldt.toLocalDate()
        val time = ldt.format(timeFmt)
        return when {
            date == today -> "Today, $time"
            date == today.minusDays(1) -> "Yesterday, $time"
            date.year == today.year -> "${date.format(dayMonth)}, $time"
            else -> "${date.format(dayMonthYear)}, $time"
        }
    }

    /**
     * Just the clock time ("14:02"), or null when the timestamp landed exactly on midnight —
     * the marker for an SMS we parsed with a date but no time. Callers hide the field rather
     * than show a misleading "00:00".
     */
    fun timeOfDay(epochMillis: Long): String? {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
        if (ldt.hour == 0 && ldt.minute == 0) return null
        return ldt.format(timeFmt)
    }

    /** Section header: "Today" / "Yesterday" / "23 May" / "12 Jan 2025". */
    fun headerForDay(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> date.format(dayMonth)
            else -> date.format(dayMonthYear)
        }
    }

    /** Stable key per calendar day (epoch day count). Used to group rows. */
    fun dayKey(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay()
}
