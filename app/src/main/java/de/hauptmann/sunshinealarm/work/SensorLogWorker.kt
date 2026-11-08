package de.hauptmann.sunshinealarm.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.hauptmann.sunshinealarm.SunshineApp
import de.hauptmann.sunshinealarm.api.SomneoException
import java.util.concurrent.TimeUnit

/** Reads the sensors every 15 minutes (Android's minimum) and stores them as history */
class SensorLogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SunshineApp
        if (app.settings.host == null) return Result.success()
        return try {
            app.repository.recordSensors()
            app.repository.updateSleepNights(days = 2)
            Result.success()
        } catch (e: SomneoException) {
            // Phone not at home or device offline: skip this slot, no retry storm
            Result.success()
        }
    }

    companion object {
        private const val NAME = "sensor-log"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SensorLogWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            // KEEP: an app update does not reset the running schedule
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
