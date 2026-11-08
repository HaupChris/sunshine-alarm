package de.hauptmann.sunshinealarm

import android.app.Application
import de.hauptmann.sunshinealarm.data.AppDatabase
import de.hauptmann.sunshinealarm.data.Repository
import de.hauptmann.sunshinealarm.data.Settings
import de.hauptmann.sunshinealarm.nap.NapController
import de.hauptmann.sunshinealarm.work.SensorLogWorker

class SunshineApp : Application() {
    val settings by lazy { Settings(this) }
    val repository by lazy { Repository(AppDatabase.get(this), settings) }
    val nap by lazy { NapController(this) }

    override fun onCreate() {
        super.onCreate()
        SensorLogWorker.schedule(this)
    }
}
