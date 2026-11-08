package de.hauptmann.sunshinealarm.nap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hauptmann.sunshinealarm.SunshineApp
import de.hauptmann.sunshinealarm.ui.Horizon
import de.hauptmann.sunshinealarm.ui.PrimaryButton
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.SunshineTheme
import de.hauptmann.sunshinealarm.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Full-screen notice over the lock screen when a nap is over */
class NapRingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        super.onCreate(savedInstanceState)
        val nap = (application as SunshineApp).nap
        setContent {
            SunshineTheme {
                val state by nap.state.collectAsStateWithLifecycle()
                LaunchedEffect(state) { if (state !is NapState.Ringing) finish() }
                val since = (state as? NapState.Ringing)?.since ?: System.currentTimeMillis()
                Column(
                    Modifier.fillMaxSize().background(Palette.background).padding(Space.page),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Nap vorbei", style = Type.title, color = Palette.text)
                    Spacer(Modifier.height(Space.s))
                    Text(
                        "seit " + Instant.ofEpochMilli(since).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = Type.value,
                        color = Palette.accent,
                    )
                    Spacer(Modifier.height(Space.xxl))
                    Horizon(1f)
                    Spacer(Modifier.height(Space.section))
                    PrimaryButton("Stoppen", { nap.stop() }, Modifier.fillMaxWidth().height(56.dp))
                }
            }
        }
    }
}
