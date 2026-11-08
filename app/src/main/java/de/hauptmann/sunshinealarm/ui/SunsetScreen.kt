package de.hauptmann.sunshinealarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hauptmann.sunshinealarm.api.SunsetSound
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.Type

/** Settings of the sunset: the lamp fading out, optionally with a calm sound. Starting it is done outside. */
@Composable
fun SunsetScreen(vm: MainViewModel, draft: SunsetDraft) {
    val now by vm.now.collectAsStateWithLifecycle()
    val themes by vm.themes.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val s = draft.settings
    var askDiscard by remember { mutableStateOf(false) }
    // Position of the bar in the curve; starts at the beginning = full brightness
    var scrub by remember { mutableFloatStateOf(0f) }
    val back = { if (draft.dirty) askDiscard = true else vm.closeSunset() }
    BackHandler(onBack = back)

    Column(Modifier.fillMaxSize().background(Palette.background)) {
        EditorBar("Sonnenuntergang", back, preview = preview, onPreview = vm::setPreview)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
            Text(
                "Die Lampe wird langsam dunkler, bis sie ausgeht. Ein ruhiges Geräusch oder Radio wird dabei leiser.",
                style = Type.secondary,
                color = Palette.textSecondary,
                modifier = Modifier.padding(top = Space.s),
            )
            Spacer(Modifier.height(Space.xxl))
            Card {
                SunriseCurve(
                    s.minutes, s.level, s.lightTheme, now.hour, now.minute,
                    height = 96.dp, descending = true, startLabel = "Start",
                    scrub = if (preview) scrub else null,
                    onScrub = { f ->
                        scrub = f
                        vm.showLightAt(s.lightTheme, curveLevel(s.level, f, descending = true))
                    },
                )
            }
            if (!preview) Hint("Mit der Vorschau oben rechts zeigt der Wecker Farbe und Helligkeit, die Kurve wird verschiebbar.", Modifier.padding(top = Space.s))
            Spacer(Modifier.height(Space.l))
            LabeledSlider("Dauer", s.minutes, 5..60, step = 5, format = { "$it min" }) { v -> vm.updateSunset { it.copy(minutes = v) } }
            LabeledSlider("Helligkeit zu Beginn", s.level, 1..25, format = { "$it von 25" }, onDone = vm::showCurrentLight) { v ->
                vm.updateSunset { it.copy(level = v) }
            }
            ChoiceList(
                options = themes?.duskLight ?: listOf("Sunny day"),
                selected = s.lightTheme,
                onSelect = { i ->
                    vm.updateSunset { it.copy(lightTheme = i) }
                    vm.showCurrentLight()
                },
                leading = { i -> ThemeSwatch(i, reversed = true) },
            )

            Spacer(Modifier.height(Space.section))
            SectionLabel("Ton beim Einschlafen")
            ToggleRow("Ton", s.sound != SunsetSound.OFF, { on ->
                vm.updateSunset { it.copy(sound = if (on) SunsetSound.NATURE else SunsetSound.OFF, soundChannel = 1) }
                vm.playCurrentPreview()
            })
            if (s.sound != SunsetSound.OFF) {
                Spacer(Modifier.height(Space.s))
                Segmented(
                    options = listOf(SunsetSound.NATURE to "Geräusch", SunsetSound.RADIO to "Radio"),
                    selected = s.sound,
                    onSelect = { src ->
                        vm.updateSunset { it.copy(sound = src, soundChannel = 1) }
                        vm.playCurrentPreview()
                    },
                )
                Spacer(Modifier.height(Space.s))
                ChoiceList(
                    options = if (s.sound == SunsetSound.NATURE) themes?.duskSounds ?: listOf("Soft Rain")
                    else (1..5).map { radioLabel(it, presets) },
                    selected = s.soundChannel - 1,
                    onSelect = { i ->
                        vm.updateSunset { it.copy(soundChannel = i + 1) }
                        vm.playCurrentPreview()
                    },
                )
                LabeledSlider("Lautstärke", s.volume, 1..25, format = { "$it von 25" }, onDone = vm::playCurrentPreview) { v ->
                    vm.updateSunset { it.copy(volume = v) }
                }
            }
            Spacer(Modifier.height(Space.section))
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(Space.page)) {
            PrimaryButton("Speichern", vm::saveSunset, Modifier.fillMaxWidth(), pending = Pending.SAVE in pending)
        }
    }
    if (askDiscard) DiscardDialog(onDiscard = { askDiscard = false; vm.closeSunset() }, onKeep = { askDiscard = false })
}
