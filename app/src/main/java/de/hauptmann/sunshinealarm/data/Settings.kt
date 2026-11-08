package de.hauptmann.sunshinealarm.data

import android.content.Context
import androidx.core.content.edit
import de.hauptmann.sunshinealarm.api.PinStore

/** Small key-value settings: device address and its pinned certificate */
class Settings(context: Context) : PinStore {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var host: String?
        get() = prefs.getString(KEY_HOST, null)
        set(value) = prefs.edit {
            // A new address means a possibly different device, so forget the old pin
            if (value != host) {
                remove(KEY_PIN)
                remove(KEY_NAME)
            }
            putString(KEY_HOST, value)
        }

    /** Friendly name from the device's UPnP description, e.g. "Wake-up Strahler" */
    var deviceName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit { putString(KEY_NAME, value) }

    /** Usual time to go to bed and to get up, minutes after midnight. Defines the "night" for the history. */
    var bedtimeMinutes: Int
        get() = prefs.getInt(KEY_BEDTIME, 23 * 60)
        set(value) = prefs.edit { putInt(KEY_BEDTIME, value) }

    var wakeMinutes: Int
        get() = prefs.getInt(KEY_WAKE, 7 * 60)
        set(value) = prefs.edit { putInt(KEY_WAKE, value) }

    /** Names for the radio presets 1..5; the device itself stores only frequencies */
    fun presetName(preset: Int): String? = prefs.getString("preset_name_$preset", null)?.takeIf { it.isNotBlank() }

    fun setPresetName(preset: Int, name: String) = prefs.edit { putString("preset_name_$preset", name.trim()) }

    /** Gesture hints the user has already used, so they are not shown again */
    val seenHints: Set<String> get() = prefs.getStringSet("seen_hints", emptySet()).orEmpty().toSet()

    fun markHintSeen(key: String) = prefs.edit { putStringSet("seen_hints", seenHints + key) }

    override var fingerprint: String?
        get() = prefs.getString(KEY_PIN, null)
        set(value) = prefs.edit { putString(KEY_PIN, value) }

    private companion object {
        const val KEY_HOST = "host"
        const val KEY_PIN = "cert_sha256"
        const val KEY_NAME = "device_name"
        const val KEY_BEDTIME = "bedtime_minutes"
        const val KEY_WAKE = "wake_minutes"
    }
}
