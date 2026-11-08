package de.hauptmann.sunshinealarm.api

import kotlinx.serialization.Serializable

/*
 * Raw payloads of the Somneo local API (https://<ip>/di/v1/products/1/<endpoint>).
 * Field names are the device's five-letter codes; Kotlin names say what they mean.
 * Reverse engineered by the pysomneo / openHAB projects, verified on an HF367x.
 */

/** GET wusrd */
@Serializable
data class SensorPayload(
    val mslux: Double, // measured illuminance, lux
    val mstmp: Double, // measured temperature, °C
    val msrhu: Double, // measured relative humidity, %
    val mssnd: Int,    // measured noise, dB
    val avlux: Double? = null,
    val avtmp: Double? = null,
    val avrhu: Double? = null,
    val avsnd: Int? = null,
)

/** GET wusts */
@Serializable
data class StatusPayload(
    val wusts: Int,          // device state, see [DeviceState]
    val snztm: Int,          // snooze time, minutes
    val brght: Int,          // display brightness 1..6
    val dspon: Boolean,      // display permanently on
    val pwrsz: Boolean = false,
)

/** GET/PUT wulgt */
@Serializable
data class LightPayload(
    val ltlvl: Int,      // brightness 1..25
    val ctype: Int,      // color scheme of the main light
    val onoff: Boolean,  // main light
    val ngtlt: Boolean,  // night light
    val tempy: Boolean,  // temporary (preview) mode
)

/** GET wualm/aenvs */
@Serializable
data class AlarmEnabledPayload(
    val prfen: List<Boolean>, // enabled per slot
    val prfvs: List<Boolean>, // slot in use ("visible") per slot
    val pwrsv: List<Int>,     // PowerWake, triples [on, hour, minute] per slot
)

/** GET wualm/aalms */
@Serializable
data class AlarmTimesPayload(
    val almhr: List<Int>,
    val almmn: List<Int>,
    val daynm: List<Int>, // weekday bitmask per slot, see [Weekday]
)

/** GET wualm/prfwu, details of the currently selected alarm slot */
@Serializable
data class AlarmDetailPayload(
    val prfnr: Int,
    val prfen: Boolean,
    val prfvs: Boolean,
    val pname: String = "",  // alarm name, stored on the device
    val ayear: Int = 0,      // date of a one-time alarm, 0 = none
    val amnth: Int = 0,
    val alday: Int = 0,
    val almhr: Int,
    val almmn: Int,
    val daynm: Int,
    val curve: Int,   // sunrise brightness 1..25
    val durat: Int,   // sunrise duration, minutes
    val ctype: Int,   // sunrise color scheme
    val snddv: String, // sound source: "wus" wake-up sound, "fmr" radio, "off"
    val sndch: String, // sound/channel number
    val sndlv: Int,   // volume 1..25
    val pwrsz: Int,   // PowerWake on/off
    val pszhr: Int,
    val pszmn: Int,
)

/** GET wutim */
@Serializable
data class DeviceTimePayload(
    val yrltm: Int, val moltm: Int, val dtltm: Int,
    val hrltm: Int, val miltm: Int, val scltm: Int,
)

/** Values of wusts.wusts, taken from pysomneo */
enum class DeviceState { OFF, SWITCHING_OFF, LIGHT_ON, SUNSET, SNOOZE, WAKE_UP, ON, UNKNOWN;

    companion object {
        fun of(code: Int) = when (code) {
            1 -> OFF
            2 -> SWITCHING_OFF
            257 -> LIGHT_ON
            264, 265, 776, 777 -> SUNSET
            2321 -> SNOOZE
            2309 -> WAKE_UP
            2817 -> ON
            else -> UNKNOWN
        }
    }
}

/** Bits of daynm. 0 means "once, next occurrence". */
enum class Weekday(val bit: Int) {
    MON(2), TUE(4), WED(8), THU(16), FRI(32), SAT(64), SUN(128);

    companion object {
        const val EVERY_DAY = 254
        const val WORKDAYS = 62
        const val WEEKEND = 192
        fun fromMask(mask: Int): Set<Weekday> = entries.filter { mask and it.bit != 0 }.toSet()
        fun toMask(days: Set<Weekday>): Int = days.sumOf { it.bit }
    }
}

enum class SoundSource(val code: String) {
    WAKE_UP("wus"), RADIO("fmr"), OFF("off");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: OFF
    }
}

/**
 * Everything the app can edit about one alarm slot.
 * lightTheme is 0-based (index into files/lightthemes), soundChannel is the 1-based
 * key of files/wakeup for wake-up sounds or the FM preset 1..5 for radio.
 */
data class AlarmSettings(
    val slot: Int,
    val name: String,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val dayMask: Int,
    val sunriseMinutes: Int,
    val sunriseLevel: Int,
    val lightTheme: Int,
    val soundSource: SoundSource,
    val soundChannel: Int,
    val volume: Int,
    /** Exact date for one-time alarms (dayMask 0), e.g. a nap */
    val date: java.time.LocalDate? = null,
) {
    companion object {
        const val MAX_NAME_LENGTH = 16

        fun from(p: AlarmDetailPayload) = AlarmSettings(
            slot = p.prfnr, name = p.pname.trim().takeUnless { it == "Alarm" }.orEmpty(), enabled = p.prfen, hour = p.almhr, minute = p.almmn, dayMask = p.daynm,
            sunriseMinutes = p.durat, sunriseLevel = p.curve, lightTheme = p.ctype,
            soundSource = SoundSource.of(p.snddv), soundChannel = p.sndch.trim().toIntOrNull() ?: 1,
            volume = p.sndlv,
            date = if (p.ayear > 0) runCatching { java.time.LocalDate.of(p.ayear, p.amnth, p.alday) }.getOrNull() else null,
        )

        /** Defaults of a fresh slot, same values the Philips app and pysomneo use */
        fun newAlarm(slot: Int, hour: Int = 7, minute: Int = 0) = AlarmSettings(
            slot = slot, name = "", enabled = true, hour = hour, minute = minute, dayMask = Weekday.WORKDAYS,
            sunriseMinutes = 30, sunriseLevel = 20, lightTheme = 0,
            soundSource = SoundSource.WAKE_UP, soundChannel = 1, volume = 12,
        )
    }
}

/** One of the 16 alarm slots, combined from aenvs and aalms */
data class Alarm(
    val slot: Int,          // 1..16, the device's prfnr
    val inUse: Boolean,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val dayMask: Int,
    val powerWake: Boolean,
    val powerWakeHour: Int,
    val powerWakeMinute: Int,
) {
    val days: Set<Weekday> get() = Weekday.fromMask(dayMask)
}

fun combineAlarms(enabled: AlarmEnabledPayload, times: AlarmTimesPayload): List<Alarm> =
    enabled.prfen.indices.map { i ->
        Alarm(
            slot = i + 1,
            inUse = enabled.prfvs.getOrElse(i) { false },
            enabled = enabled.prfen[i],
            hour = times.almhr.getOrElse(i) { 0 },
            minute = times.almmn.getOrElse(i) { 0 },
            dayMask = times.daynm.getOrElse(i) { 0 },
            powerWake = enabled.pwrsv.getOrElse(3 * i) { 0 } != 0,
            powerWakeHour = enabled.pwrsv.getOrElse(3 * i + 1) { 0 },
            powerWakeMinute = enabled.pwrsv.getOrElse(3 * i + 2) { 0 },
        )
    }

/** GET/PUT wuply, the audio player (radio, AUX, previews) */
@Serializable
data class PlayerPayload(
    val onoff: Boolean,
    val sdvol: Int,          // volume 1..25
    val snddv: String,       // "fmr" radio, "aux", "wus" wake-up sound, "dus" dusk sound
    val sndch: String = "",  // preset or sound number
    val tempy: Boolean = false, // true while a preview plays
)

/** GET/PUT wudsk, the sunset (wind-down) program */
@Serializable
data class SunsetPayload(
    val onoff: Boolean,
    val durat: Int,    // minutes 5..60
    val curve: Int,    // start brightness 1..25
    val ctype: Int,    // light theme, 0-based (files/dusklightthemes)
    val snddv: String, // "off", "dus" dusk sound, "fmr" radio
    val sndch: String = "",
    val sndlv: Int,    // volume 1..25
)

/** GET wutmr, remaining time of running programs */
@Serializable
data class TimerPayload(val dskmn: Int = 0, val dsksc: Int = 0)

enum class SunsetSound(val code: String) {
    OFF("off"), NATURE("dus"), RADIO("fmr");

    companion object {
        fun of(code: String) = entries.firstOrNull { it.code == code } ?: OFF
    }
}

data class SunsetSettings(
    val running: Boolean,
    val minutes: Int,
    val level: Int,
    val lightTheme: Int,
    val sound: SunsetSound,
    val soundChannel: Int,
    val volume: Int,
) {
    companion object {
        fun from(p: SunsetPayload) = SunsetSettings(
            running = p.onoff, minutes = p.durat, level = p.curve, lightTheme = p.ctype,
            sound = SunsetSound.of(p.snddv), soundChannel = p.sndch.trim().toIntOrNull() ?: 1, volume = p.sndlv,
        )
    }
}
