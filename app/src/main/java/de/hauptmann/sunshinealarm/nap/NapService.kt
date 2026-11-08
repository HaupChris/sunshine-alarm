package de.hauptmann.sunshinealarm.nap

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import de.hauptmann.sunshinealarm.R
import de.hauptmann.sunshinealarm.SunshineApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fired by the exact alarm at the end of a nap */
class NapAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as SunshineApp).nap.ring()
    }
}

/** Sets the alarm again after a reboot */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) (context.applicationContext as SunshineApp).nap.reschedule()
    }
}

/**
 * Runs while a nap rings: shows the full-screen "nap over" notice on the phone and
 * drives the wake-up light: a sunrise over about 10 s together with the sound,
 * repeated so the device's preview does not time out, until stopped.
 */
class NapService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> ring()
            ACTION_STOP -> stopRinging()
        }
        return START_NOT_STICKY
    }

    private fun ring() {
        val app = application as SunshineApp
        val plan = (app.nap.state.value as? NapState.Ringing)?.plan ?: return stopSelf()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        job?.cancel()
        job = scope.launch {
            val client = runCatching { app.repository.client() }.getOrNull() ?: return@launch
            runCatching {
                if (plan.withSound) client.preview("wus", plan.channel, plan.volume)
                if (plan.withLight) {
                    for (step in 1..RAMP_STEPS) {
                        val level = (plan.level * step / RAMP_STEPS).coerceAtLeast(1)
                        client.lightPreview(plan.theme, level)
                        delay(RAMP_MS / RAMP_STEPS)
                    }
                }
            }
            // Keep light and sound alive until stopped, at most MAX_RING_MS
            val started = System.currentTimeMillis()
            while (System.currentTimeMillis() - started < MAX_RING_MS) {
                delay(KEEPALIVE_MS)
                runCatching {
                    if (plan.withSound) client.preview("wus", plan.channel, plan.volume)
                    if (plan.withLight) client.lightPreview(plan.theme, plan.level)
                }
            }
            app.nap.stop()
        }
    }

    private fun stopRinging() {
        job?.cancel()
        val app = application as SunshineApp
        scope.launch {
            runCatching {
                val client = app.repository.client()
                client.stopPreview()
                client.endLightPreview(client.light().copy(onoff = false, ngtlt = false))
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Nap", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Meldet das Ende eines Naps"
                setSound(null, null) // the wake-up light plays the sound
                enableVibration(true)
            },
        )
        val full = PendingIntent.getActivity(
            this, 2, Intent(this, NapRingingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            this, 3, Intent(this, NapStopReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_nap)
            .setContentTitle("Nap vorbei")
            .setContentText("Der Wecker weckt dich gerade")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .addAction(0, "Stoppen", stop)
            .build()
    }

    companion object {
        const val ACTION_RING = "ring"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "nap"
        private const val NOTIFICATION_ID = 7
        private const val RAMP_MS = 10_000L
        private const val RAMP_STEPS = 10
        private const val KEEPALIVE_MS = 20_000L
        private const val MAX_RING_MS = 15 * 60_000L
    }
}

/** "Stoppen" button in the notification */
class NapStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as SunshineApp).nap.stop()
    }
}
