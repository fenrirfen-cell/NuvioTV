package com.nuvio.tv.ui.screens.anidrama

import java.time.LocalDate
import java.time.ZoneId

/** Small calendar helpers (same as the phone app, on java.time). */
internal object AniDramaTime {
    private const val DAY_MS = 86_400_000L

    val weekdayShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun nowEpochMs(): Long = System.currentTimeMillis()

    fun todayIso(): String = LocalDate.now().toString()

    /** Days since 1970-01-01 for a yyyy-MM-dd date, or null if it can't be read. */
    fun epochDay(iso: String): Long? {
        val parts = iso.take(10).split('-')
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return daysFromCivil(y, m, d)
    }

    fun isoFromEpochDay(day: Long): String {
        val (y, m, d) = civilFromDays(day)
        return "${y.toString().padStart(4, '0')}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }

    fun addDays(iso: String, days: Int): String = epochDay(iso)?.let { isoFromEpochDay(it + days) } ?: iso

    /** 0 = Monday ... 6 = Sunday. 1970-01-01 was a Thursday. */
    fun weekdayOfEpochDay(day: Long): Int = (((day + 3) % 7 + 7) % 7).toInt()

    fun weekdayOfIso(iso: String): Int? = epochDay(iso)?.let(::weekdayOfEpochDay)

    /** Monday of the current week, as yyyy-MM-dd. */
    fun mondayOfThisWeek(): String {
        val today = todayIso()
        val day = epochDay(today) ?: return today
        return isoFromEpochDay(day - weekdayOfEpochDay(day))
    }

    /** Local midnight (epoch ms) of a yyyy-MM-dd date. */
    fun localStartOfDayMs(iso: String): Long =
        runCatching {
            LocalDate.parse(iso.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull() ?: ((epochDay(iso) ?: 0L) * DAY_MS)

    /** Local date (yyyy-MM-dd) of a moment, found by walking from today. */
    fun localDayIsoOf(epochMs: Long): String {
        var day = todayIso()
        var guard = 0
        while (epochMs < localStartOfDayMs(day) && guard++ < 4000) day = addDays(day, -1)
        while (epochMs >= localStartOfDayMs(addDays(day, 1)) && guard++ < 4000) day = addDays(day, 1)
        return day
    }

    /** Local time label (e.g. "9:30 PM") of a moment. */
    fun localTimeLabelOf(epochMs: Long): String {
        val minutes = ((epochMs - localStartOfDayMs(localDayIsoOf(epochMs))) / 60_000L).toInt()
        return timeLabel(minutes / 60, minutes % 60)
    }

    fun firstOfMonth(year: Int, month: Int): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-01"

    fun daysInMonth(year: Int, month: Int): Int {
        val start = epochDay(firstOfMonth(year, month)) ?: return 30
        val next = if (month == 12) firstOfMonth(year + 1, 1) else firstOfMonth(year, month + 1)
        return ((epochDay(next) ?: (start + 30)) - start).toInt()
    }

    private val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    fun monthName(month: Int): String = monthNames.getOrElse(month - 1) { "" }

    /** "Sat, Nov 15". */
    fun shortDayLabel(iso: String): String {
        val weekday = weekdayOfIso(iso)?.let { weekdayShort[it] }.orEmpty()
        val month = iso.substring(5, 7).toIntOrNull()?.let { monthNames.getOrNull(it - 1)?.take(3) }.orEmpty()
        val day = iso.substring(8, 10).toIntOrNull() ?: 0
        return "$weekday, $month $day"
    }

    fun longDayLabel(iso: String): String {
        val weekday = weekdayOfIso(iso)?.let { listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[it] }.orEmpty()
        val month = iso.substring(5, 7).toIntOrNull()?.let { monthNames.getOrNull(it - 1)?.take(3) }.orEmpty()
        val day = iso.substring(8, 10).toIntOrNull() ?: 0
        return "$weekday, $month $day"
    }

    /** Local weekday (0 = Monday) and time label for a moment in epoch seconds, within this week. */
    fun localWeekdayAndTime(epochSeconds: Long, weekStartIso: String): Pair<Int, String>? {
        val ms = epochSeconds * 1000
        for (offset in 0..6) {
            val dayIso = addDays(weekStartIso, offset)
            val start = localStartOfDayMs(dayIso)
            val end = localStartOfDayMs(addDays(dayIso, 1))
            if (ms in start until end) {
                val minutes = ((ms - start) / 60_000L).toInt()
                return offset to timeLabel(minutes / 60, minutes % 60)
            }
        }
        return null
    }

    fun timeLabel(hour24: Int, minute: Int): String {
        val suffix = if (hour24 < 12) "AM" else "PM"
        val hour12 = when (val h = hour24 % 12) { 0 -> 12; else -> h }
        return "$hour12:${minute.toString().padStart(2, '0')} $suffix"
    }

    /** The anime season (and its AniList year) that a date falls in. December counts as next year's winter. */
    fun currentAnimeSeason(): Pair<AnimeSeason, Int> {
        val parts = todayIso().split('-')
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
        return when (month) {
            12 -> AnimeSeason.Winter to year + 1
            1, 2 -> AnimeSeason.Winter to year
            3, 4, 5 -> AnimeSeason.Spring to year
            6, 7, 8 -> AnimeSeason.Summer to year
            else -> AnimeSeason.Fall to year
        }
    }

    // Howard Hinnant's civil-date algorithms.
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    private fun civilFromDays(days: Long): Triple<Int, Int, Int> {
        val z = days + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        return Triple((if (m <= 2) y + 1 else y).toInt(), m, d)
    }
}
