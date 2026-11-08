package de.hauptmann.sunshinealarm.nap

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import de.hauptmann.sunshinealarm.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What happens when a nap ends. The device itself can only do sunrises of 5 min
 * or more, so the app drives a short sunrise (about 10 s) and the sound itself.
 */
@Serializable
data class NapPlan(
    val minutes: Int = 20,
    val withLight: Boolean = true,
    val theme: Int = 0,      // light theme, 0-based
    val level: Int = 20,     // final brightness 1..25
    val withSound: Boolean = true,
    val channel: Int = 1,    // wake-up sound, 1-based
    val volume: Int = 8,
)

@Serializable
sealed interface NapState {
    @Serializable data object Idle : NapState
    @Serializable data class Running(val endsAt: Long, val plan: NapPlan) : NapState
    @Serializable data class Ringing(val since: Long, val plan: NapPlan) : NapState
}

/**
 * Keeps the nap state (persisted, survives app restarts) and the exact Android
 * alarm that fires at the end of the nap.
 */
class NapController(private val context: Context) {
    private val prefs = context.getSharedPreferences("nap", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<NapState> = _state.asStateFlow()

    var lastPlan: NapPlan
        get() = prefs.getString(KEY_PLAN, null)?.let { runCatching { json.decodeFromString(NapPlan.serializer(), it) }.getOrNull() } ?: NapPlan()
        set(value) = prefs.edit { putString(KEY_PLAN, json.encodeToString(NapPlan.serializer(), value)) }

    fun start(plan: NapPlan, now: Long = System.currentTimeMillis()) {
        lastPlan = plan
        val endsAt = now + plan.minutes * 60_000L
        set(NapState.Running(endsAt, plan))
        schedule(endsAt)
    }

    /** Called by the alarm: switch to ringing and start the service that drives the light */
    fun ring() {
        val s = _state.value as? NapState.Running ?: return
        set(NapState.Ringing(System.currentTimeMillis(), s.plan))
        ContextCompat.startForegroundService(context, Intent(context, NapService::class.java).setAction(NapService.ACTION_RING))
    }

    /** Cancels a running nap or stops a ringing one */
    fun stop() {
        val wasRinging = _state.value is NapState.Ringing
        cancelAlarm()
        set(NapState.Idle)
        if (wasRinging) {
            context.startService(Intent(context, NapService::class.java).setAction(NapService.ACTION_STOP))
        }
    }

    /** After a reboot the Android alarm is gone; set it again if the nap is still running */
    fun reschedule() {
        when (val s = _state.value) {
            is NapState.Running -> if (s.endsAt > System.currentTimeMillis()) schedule(s.endsAt) else ring()
            else -> Unit
        }
    }

    private fun schedule(at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        // setAlarmClock is exact even in Doze and shows the alarm icon in the status bar
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), firePending())
    }

    private fun cancelAlarm() {
        context.getSystemService(AlarmManager::class.java).cancel(firePending())
    }

    private fun firePending() = PendingIntent.getBroadcast(
        context, 1, Intent(context, NapAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun set(s: NapState) {
        _state.value = s
        prefs.edit { putString(KEY_STATE, json.encodeToString(NapState.serializer(), s)) }
    }

    private fun load(): NapState = prefs.getString(KEY_STATE, null)
        ?.let { runCatching { json.decodeFromString(NapState.serializer(), it) }.getOrNull() } ?: NapState.Idle

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_PLAN = "plan"
    }
}
