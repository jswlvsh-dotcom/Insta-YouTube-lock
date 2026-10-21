package com.jaivish.gowork.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** ISO date ("2026-10-21") of [epochMs] in [zone]; used as the key for daily stats. */
fun dayKey(epochMs: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toString()

/** "1h 05m", "23m", "40s". */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "%dh %02dm".format(hours, minutes)
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

/** "1:05" for countdowns. */
fun formatClockDuration(ms: Long): String {
    val totalSeconds = ((ms + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private val shortTime: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatMinuteOfDay(minuteOfDay: Int): String =
    LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), minuteOfDay % 60).format(shortTime)

fun formatTimeOfDay(epochMs: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime().format(shortTime)
