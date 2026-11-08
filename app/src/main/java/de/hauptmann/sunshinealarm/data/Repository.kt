package de.hauptmann.sunshinealarm.data

import de.hauptmann.sunshinealarm.api.Alarm
import de.hauptmann.sunshinealarm.api.AlarmSettings
import de.hauptmann.sunshinealarm.api.LightPayload
import de.hauptmann.sunshinealarm.api.PlayerPayload
import de.hauptmann.sunshinealarm.api.SensorPayload
import de.hauptmann.sunshinealarm.api.SomneoClient
import de.hauptmann.sunshinealarm.api.SoundSource
import de.hauptmann.sunshinealarm.api.SunsetSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Names of the device's light and sound themes */
data class Themes(
    val light: List<String>,
    val wakeSounds: List<String>,
    val duskLight: List<String>,
    val duskSounds: List<String>,
)

/** Talks to the device and mirrors what it reads into the local database */
class Repository(
    private val db: AppDatabase,
    private val settings: Settings,
) {
    private var client: SomneoClient? = null
    private var clientHost: String? = null
    private var themes: Themes? = null

    val alarms = db.alarmDao().all()
    val latestReading = db.sensorDao().latest()
    val readingCount = db.sensorDao().count()
    fun readingsSince(since: Long) = db.sensorDao().since(since)
    fun recentNights(limit: Int = 14) = db.sleepDao().recent(limit)

    /** Detects sleep times from the light history for the last [days] nights; corrections are kept */
    suspend fun updateSleepNights(days: Int = 14, today: LocalDate = LocalDate.now()) {
        val zone = ZoneId.systemDefault()
        for (i in 1..days) {
            val night = today.minusDays(i.toLong())
            val from = night.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
            val to = night.plusDays(1).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
            val detected = detectSleep(db.sensorDao().between(from, to), night, zone)
            val existing = db.sleepDao().get(night.toString())
            if (detected == null && existing == null) continue
            db.sleepDao().upsert(
                (existing ?: SleepNight(night.toString(), null, null))
                    .copy(detectedStart = detected?.first, detectedEnd = detected?.second),
            )
        }
    }

    suspend fun adjustNight(night: String, start: Long?, end: Long?) {
        val existing = db.sleepDao().get(night) ?: SleepNight(night, null, null)
        db.sleepDao().upsert(existing.copy(start = start, end = end))
    }

    fun client(): SomneoClient {
        val host = settings.host ?: error("No wake-up light configured")
        if (client == null || clientHost != host) {
            client = SomneoClient.create(host, settings)
            clientHost = host
            themes = null
        }
        return client!!
    }

    suspend fun recordSensors(now: Long = System.currentTimeMillis()): SensorPayload {
        val s = client().sensors()
        db.sensorDao().insert(
            SensorReading(timestamp = now, lux = s.mslux, temperatureC = s.mstmp, humidityPercent = s.msrhu, noiseDb = s.mssnd),
        )
        return s
    }

    suspend fun light(): LightPayload = client().light()
    suspend fun player(): PlayerPayload = client().player()
    suspend fun radioPresets(): Map<Int, String> = client().radioPresets()
    suspend fun sunset(): SunsetSettings = client().sunset()

    suspend fun themes(): Themes = themes ?: client().let { c ->
        Themes(
            light = c.themes("files/lightthemes").toSortedMap().values.toList(),
            wakeSounds = c.themes("files/wakeup").toSortedMap().values.toList(),
            duskLight = c.themes("files/dusklightthemes").toSortedMap().values.toList(),
            duskSounds = c.themes("files/winddowndusk").toSortedMap().values.toList(),
        )
    }.also { themes = it }

    /**
     * Reads all slots plus the full profile of every slot in use. Local-only data
     * (nap end) is kept, and naps that are over are removed from the device.
     */
    suspend fun syncAlarms(now: Long = System.currentTimeMillis()): List<Alarm> {
        val alarms = client().alarms()
        val known = alarms.associate { it.slot to db.alarmDao().get(it.slot) }
        val entities = alarms.map { a ->
            val details = if (a.inUse) client().alarmSettings(a.slot) else null
            val napEndsAt = known[a.slot]?.napEndsAt?.takeIf { a.inUse }
            a.toEntity(now, details, napEndsAt)
        }
        db.alarmDao().upsertAll(entities)

        val finishedNaps = entities.filter { it.napEndsAt != null && now > it.napEndsAt + NAP_GRACE_MS }
        if (finishedNaps.isNotEmpty()) {
            finishedNaps.forEach { client().removeAlarm(it.slot) }
            return syncAlarms(now)
        }
        return alarms
    }

    suspend fun alarmSettings(slot: Int): AlarmSettings = client().alarmSettings(slot)

    /*
     * Writes touch one slot only: the device call, then the local copy of that slot.
     * A full sync reads every slot (2 + 2 per alarm requests, ~0.5 s each) and
     * runs on refresh instead.
     */
    suspend fun setAlarmEnabled(slot: Int, enabled: Boolean) {
        client().setAlarmEnabled(slot, enabled)
        db.alarmDao().get(slot)?.let { db.alarmDao().upsertAll(listOf(it.copy(enabled = enabled))) }
    }

    /** Local change only, shown right away while the device call is running */
    suspend fun setAlarmEnabledLocally(slot: Int, enabled: Boolean) {
        db.alarmDao().get(slot)?.let { db.alarmDao().upsertAll(listOf(it.copy(enabled = enabled))) }
    }

    suspend fun saveAlarm(settings: AlarmSettings) {
        client().saveAlarm(settings)
        val existing = db.alarmDao().get(settings.slot)
        db.alarmDao().upsertAll(listOf(settings.toEntity(System.currentTimeMillis(), existing)))
    }

    suspend fun removeAlarm(slot: Int) {
        client().removeAlarm(slot)
        db.alarmDao().get(slot)?.let {
            db.alarmDao().upsertAll(listOf(it.copy(inUse = false, enabled = false, name = null, napEndsAt = null)))
        }
    }

    suspend fun cachedAlarm(slot: Int): AlarmEntity? = db.alarmDao().get(slot)

    /** First slot not in use, or null if all 16 are taken */
    suspend fun freeSlot(): Int? = client().alarms().firstOrNull { !it.inUse }?.slot

    private fun Alarm.toEntity(now: Long, d: AlarmSettings?, napEndsAt: Long?) = AlarmEntity(
        slot = slot, inUse = inUse, enabled = enabled, hour = hour, minute = minute,
        dayMask = dayMask, powerWake = powerWake, syncedAt = now,
        sunriseMinutes = d?.sunriseMinutes, sunriseLevel = d?.sunriseLevel, lightTheme = d?.lightTheme,
        soundSource = d?.soundSource?.code, soundChannel = d?.soundChannel, volume = d?.volume,
        // "Alarm" is the device's default name, not one the user chose
        name = d?.name?.takeUnless { it.isBlank() || it == "Alarm" }, napEndsAt = napEndsAt,
    )

    private companion object {
        const val NAP_GRACE_MS = 30 * 60 * 1000L
    }
}

fun AlarmSettings.toEntity(now: Long, existing: AlarmEntity?) = AlarmEntity(
    slot = slot, inUse = true, enabled = enabled, hour = hour, minute = minute, dayMask = dayMask,
    powerWake = existing?.powerWake ?: false, syncedAt = now,
    sunriseMinutes = sunriseMinutes, sunriseLevel = sunriseLevel, lightTheme = lightTheme,
    soundSource = soundSource.code, soundChannel = soundChannel, volume = volume,
    name = name.takeUnless { it.isBlank() || it == "Alarm" }, napEndsAt = existing?.napEndsAt,
)

fun AlarmEntity.toSettings() = AlarmSettings(
    slot = slot, name = name.orEmpty(), enabled = enabled, hour = hour, minute = minute, dayMask = dayMask,
    sunriseMinutes = sunriseMinutes ?: 30, sunriseLevel = sunriseLevel ?: 20, lightTheme = lightTheme ?: 0,
    soundSource = SoundSource.of(soundSource ?: "wus"), soundChannel = soundChannel ?: 1, volume = volume ?: 12,
)
