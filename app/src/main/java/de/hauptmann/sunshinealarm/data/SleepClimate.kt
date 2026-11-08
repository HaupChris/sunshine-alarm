package de.hauptmann.sunshinealarm.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Recommended bedroom values during sleep.
 *  Temperature 16–19 °C: sleep guides (e.g. stadlerform, Philips); German UBA: bedroom at least 16 °C
 *  Humidity 40–60 %: UBA, mould risk rises above 60 %
 *  Noise up to 30 dB: WHO guideline for bedrooms (continuous noise, 30 dB LAeq)
 *  Light up to 1 lx: studies on light at night report more fragmented sleep already at about 1 lx
 */
enum class Target(val low: Double?, val high: Double?) {
    TEMPERATURE(16.0, 19.0),
    HUMIDITY(40.0, 60.0),
    NOISE(null, 30.0),
    LIGHT(null, 1.0);

    fun contains(v: Double) = (low == null || v >= low) && (high == null || v <= high)

    /** Plain word for a value, never only a color */
    fun verdict(v: Double): Verdict = when {
        low != null && v < low -> Verdict.LOW
        high != null && v > high -> Verdict.HIGH
        else -> Verdict.OK
    }
}

enum class Verdict { LOW, OK, HIGH }

data class Night(val start: LocalDateTime, val end: LocalDateTime) {
    fun startMillis(zone: ZoneId = ZoneId.systemDefault()) = start.atZone(zone).toInstant().toEpochMilli()
    fun endMillis(zone: ZoneId = ZoneId.systemDefault()) = end.atZone(zone).toInstant().toEpochMilli()
    fun contains(millis: Long, zone: ZoneId = ZoneId.systemDefault()) = millis in startMillis(zone)..endMillis(zone)
}

/** The sleep window that started on [day] evening */
fun nightOf(day: LocalDate, bedtimeMinutes: Int, wakeMinutes: Int): Night {
    val start = day.atStartOfDay().plusMinutes(bedtimeMinutes.toLong())
    var end = day.atStartOfDay().plusMinutes(wakeMinutes.toLong())
    if (!end.isAfter(start)) end = end.plusDays(1)
    return Night(start, end)
}

/** The most recent sleep window that has already begun before [now] */
fun lastNight(now: LocalDateTime, bedtimeMinutes: Int, wakeMinutes: Int): Night {
    val today = nightOf(now.toLocalDate(), bedtimeMinutes, wakeMinutes)
    return if (today.start.isAfter(now)) nightOf(now.toLocalDate().minusDays(1), bedtimeMinutes, wakeMinutes) else today
}

/** All sleep windows overlapping [fromMillis, toMillis] */
fun nightsBetween(fromMillis: Long, toMillis: Long, bedtimeMinutes: Int, wakeMinutes: Int, zone: ZoneId = ZoneId.systemDefault()): List<Night> {
    val first = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate().minusDays(1)
    val last = Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate()
    return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }
        .map { nightOf(it, bedtimeMinutes, wakeMinutes) }
        .filter { it.endMillis(zone) >= fromMillis && it.startMillis(zone) <= toMillis }
        .toList()
}

data class NightSummary(val average: Double, val min: Double, val max: Double, val shareInRange: Double, val samples: Int)

fun summarize(values: List<Double>, target: Target): NightSummary? {
    if (values.isEmpty()) return null
    return NightSummary(
        average = values.average(), min = values.min(), max = values.max(),
        shareInRange = values.count { target.contains(it) } / values.size.toDouble(), samples = values.size,
    )
}
