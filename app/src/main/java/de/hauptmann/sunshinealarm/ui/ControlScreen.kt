package de.hauptmann.sunshinealarm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hauptmann.sunshinealarm.api.SunsetSettings
import de.hauptmann.sunshinealarm.api.SunsetSound
import de.hauptmann.sunshinealarm.data.Themes
import de.hauptmann.sunshinealarm.ui.theme.Line
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.Type

/** Direct control of the device: lamp, sunset and playback. Everything here applies at once. */
@Composable
fun ControlScreen(vm: MainViewModel) {
    val light by vm.light.collectAsStateWithLifecycle()
    val sunset by vm.sunset.collectAsStateWithLifecycle()
    val sunsetRemaining by vm.sunsetRemaining.collectAsStateWithLifecycle()
    val player by vm.player.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val themes by vm.themes.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val unavailable = "Verfügbar, sobald der Wecker erreichbar ist"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
        ScreenTitle("Steuerung")

        SectionLabel("Lampe")
        light?.let { l ->
            ToggleRow("Hauptlicht", l.onoff, vm::setLight, pending = Pending.LIGHT in pending)
            if (l.onoff) {
                LabeledSlider("Helligkeit", l.ltlvl, 1..25, format = { "$it von 25" }, onChange = vm::previewLightLevel, onDone = vm::commitLightLevel)
            }
            ToggleRow("Nachtlicht", l.ngtlt, vm::setNightLight, pending = Pending.NIGHT_LIGHT in pending)
        } ?: Hint(unavailable)

        // A program with a duration: row opens the settings, the button starts or ends it
        Spacer(Modifier.height(Space.section))
        SectionLabel("Sonnenuntergang")
        sunset?.let { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Row(
                    Modifier.weight(1f).heightIn(min = Space.row).clickable(onClick = vm::openSunset),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Einstellungen", style = Type.body, color = Palette.text)
                        Text(
                            if (s.running) "läuft noch ${(sunsetRemaining + 59) / 60} min" else sunsetSummary(s, themes),
                            style = Type.secondary,
                            color = if (s.running) Palette.accent else Palette.textMuted,
                        )
                    }
                    Icon(Line.chevron, null, tint = Palette.textMuted, modifier = Modifier.padding(horizontal = Space.s).size(18.dp))
                }
                if (s.running) SecondaryButton("Beenden", { vm.setSunsetRunning(false) }, pending = Pending.SUNSET in pending)
                else PrimaryButton("Starten", { vm.setSunsetRunning(true) }, pending = Pending.SUNSET in pending)
            }
        } ?: Hint(unavailable)

        Spacer(Modifier.height(Space.section))
        SectionLabel("Wiedergabe")
        player?.let { p ->
            val on = p.onoff && !p.tempy && p.snddv in setOf("fmr", "aux")
            ToggleRow("Wiedergabe", on, vm::setPlayer, pending = Pending.PLAYER in pending)
            if (on) {
                Spacer(Modifier.height(Space.s))
                Segmented(
                    options = listOf("fmr" to "Radio", "aux" to "AUX"),
                    selected = p.snddv,
                    onSelect = { if (it == "aux") vm.playAux() else vm.playRadio(p.sndch.toIntOrNull()?.takeIf { c -> c in 1..5 } ?: 1) },
                )
                Spacer(Modifier.height(Space.s))
                if (p.snddv == "fmr") {
                    presets.forEach { preset ->
                        PresetRow(
                            preset,
                            playing = preset.number == p.sndch.toIntOrNull(),
                            onOpen = { vm.openPreset(preset.number) },
                            onPlay = { vm.playRadio(preset.number) },
                        )
                    }
                } else {
                    Hint("Spielt ab, was per Kabel am AUX-Eingang hinten am Wecker angeschlossen ist, zum Beispiel dein Handy.")
                }
                LabeledSlider("Lautstärke", p.sdvol, 1..25, format = { "$it von 25" }, onDone = vm::commitPlayerVolume, onChange = vm::previewPlayerVolume)
            } else {
                presets.forEach { preset ->
                    PresetRow(preset, playing = false, onOpen = { vm.openPreset(preset.number) }, onPlay = { vm.playRadio(preset.number) })
                }
            }
        } ?: Hint(unavailable)
        Spacer(Modifier.height(Space.section))
    }
}

/** Row tap opens the preset (name, frequency); the play button at the right plays it */
@Composable
private fun PresetRow(p: RadioPreset, playing: Boolean, onOpen: () -> Unit, onPlay: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = Space.row), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = Space.xs)) {
            Text(p.label, style = Type.body, color = Palette.text)
            Text(p.frequency ?: "", style = Type.meta, color = Palette.textMuted)
        }
        if (playing) Text("läuft", style = Type.meta, color = Palette.accent, modifier = Modifier.padding(end = Space.s))
        else IconAction(Line.play, "${p.label} abspielen", onPlay)
    }
}

private fun sunsetSummary(s: SunsetSettings, themes: Themes?): String {
    val sound = when (s.sound) {
        SunsetSound.NATURE -> themes?.duskSounds?.getOrNull(s.soundChannel - 1) ?: "Geräusch"
        SunsetSound.RADIO -> "Radio ${s.soundChannel}"
        SunsetSound.OFF -> "ohne Ton"
    }
    return listOfNotNull("${s.minutes} min", themes?.duskLight?.getOrNull(s.lightTheme), sound).joinToString(" · ")
}
