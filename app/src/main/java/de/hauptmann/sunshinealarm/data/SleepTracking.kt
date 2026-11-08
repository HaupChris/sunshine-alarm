package de.hauptmann.sunshinealarm.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/**
 * One night of sleep. Detected times come from the light sensor; the user can
 * correct them, corrections win and are never overwritten by detection.
 */
@Entity(tableName = "sleep_night")
data class SleepNight(
    /** Date of the evening the night started, ISO "2026-11-08" */
    @PrimaryKey val night: String,
    val detectedStart: Long?,
    val detectedEnd: Long?,
    val start: Long? = null,
    val end: Long? = null,
) {
    val effectiveStart get() = start ?: detectedStart
    val effectiveEnd get() = end ?: detectedEnd
    val adjusted get() = start != null || end != null
    val date: LocalDate get() = LocalDate.parse(night)
}

@Dao
interface SleepDao {
    @Upsert
    suspend fun upsert(night: SleepNight)

    @Query("SELECT * FROM sleep_night WHERE night = :night")
    suspend fun get(night: String): SleepNight?

    @Query("SELECT * FROM sleep_night ORDER BY night DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<SleepNight>>
}

/** Lux at or below this counts as "dark" (lights off; the night light alone measures about 7 lx) */
const val DARK_LUX = 3.0

/**
 * Finds the sleep period of the night starting on [night]: the longest stretch of
 * darkness between 18:00 and 14:00 the next day. Gaps without data up to 3 h
 * are bridged. Returns null if there is no dark stretch of at least 2 hours.
 */
fun detectSleep(readings: List<SensorReading>, night: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long>? {
    val from = night.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    val to = night.plusDays(1).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
    val window = readings.filter { it.timestamp in from..to }.sortedBy { it.timestamp }

    var best: Pair<Long, Long>? = null
    var runStart: Long? = null
    var lastDark: Long? = null
    fun close(endAt: Long) {
        val s = runStart ?: return
        if (best == null || endAt - s > best!!.second - best!!.first) best = s to endAt
    }
    for (r in window) {
        val dark = r.lux <= DARK_LUX
        val gapTooBig = lastDark != null && r.timestamp - lastDark!! > MAX_GAP_MS
        if (dark) {
            if (runStart == null || gapTooBig) {
                if (gapTooBig) close(lastDark!!)
                runStart = r.timestamp
            }
            lastDark = r.timestamp
        } else if (runStart != null) {
            close(r.timestamp) // woke up when it got bright
            runStart = null
            lastDark = null
        }
    }
    lastDark?.let { close(it) }
    return best?.takeIf { it.second - it.first >= MIN_SLEEP_MS }
}

// Android Doze often delays the background readings at night by an hour or more
private const val MAX_GAP_MS = 3 * 3_600_000L
private const val MIN_SLEEP_MS = 2 * 3_600_000L
