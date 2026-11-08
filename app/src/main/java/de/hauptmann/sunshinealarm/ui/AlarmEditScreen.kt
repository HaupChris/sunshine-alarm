package de.hauptmann.sunshinealarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import de.hauptmann.sunshinealarm.api.SoundSource
import de.hauptmann.sunshinealarm.api.Weekday
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.Type

@Composable
fun AlarmEditScreen(vm: MainViewModel, editing: Editing) {
    val themes by vm.themes.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val s = editing.settings
    var pickTime by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    // Position of the bar in the curve; starts at the end = full brightness
    var scrub by remember { mutableFloatStateOf(1f) }
    val back = { if (editing.dirty) askDiscard = true else vm.closeEditor() }
    BackHandler(onBack = back)

    Column(Modifier.fillMaxSize().background(Palette.background)) {
        EditorBar(
            if (editing.isNew) "Neuer Wecker" else s.name.ifBlank { "Wecker" },
            back,
            preview = preview,
            onPreview = vm::setPreview,
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
            Text(
                hhmm(s.hour, s.minute),
                style = Type.hero,
                color = Palette.text,
                maxLines = 1,
                modifier = Modifier.clickable { pickTime = true }.padding(top = Space.s),
            )
            TextAction("Uhrzeit ändern", { pickTime = true })

            Spacer(Modifier.height(Space.xl))
            SectionLabel("Name")
            NameField(s.name) { v -> vm.updateEditing { it.copy(name = v) } }

            Spacer(Modifier.height(Space.xl))
            SectionLabel("Wiederholen · ${daysLabel(s.dayMask)}")
            ChipRow(
                labels = Weekday.entries.map(::weekdayShort),
                isOn = { i -> s.dayMask and Weekday.entries[i].bit != 0 },
                onToggle = { i -> vm.updateEditing { it.copy(dayMask = it.dayMask xor Weekday.entries[i].bit) } },
                round = true,
            )

            // ---- Sunrise, with the curve as preview; with the device preview on, the bar can be moved ----
            Spacer(Modifier.height(Space.section))
            SectionLabel("Sonnenaufgang")
            Card {
                SunriseCurve(
                    s.sunriseMinutes, s.sunriseLevel, s.lightTheme, s.hour, s.minute, height = 96.dp,
                    scrub = if (preview) scrub else null,
                    onScrub = { f ->
                        scrub = f
                        vm.showLightAt(s.lightTheme, curveLevel(s.sunriseLevel, f, descending = false))
                    },
                )
            }
            if (!preview) Hint("Mit der Vorschau oben rechts zeigt der Wecker Farbe und Helligkeit, die Kurve wird verschiebbar.", Modifier.padding(top = Space.s))
            Spacer(Modifier.height(Space.l))
            LabeledSlider("Dauer", s.sunriseMinutes, 5..40, step = 5, format = { "$it min" }) { v ->
                vm.updateEditing { it.copy(sunriseMinutes = v) }
            }
            LabeledSlider("Helligkeit", s.sunriseLevel, 1..25, format = { "$it von 25" }, onDone = vm::showCurrentLight) { v ->
                vm.updateEditing { it.copy(sunriseLevel = v) }
            }
            ChoiceList(
                options = themes?.light ?: listOf("Sunny day"),
                selected = s.lightTheme,
                onSelect = { i ->
                    vm.updateEditing { it.copy(lightTheme = i) }
                    vm.showCurrentLight()
                },
                leading = { i -> ThemeSwatch(i) },
            )

            // ---- Sound: switch for on/off, segmented for the variant ----
            Spacer(Modifier.height(Space.section))
            SectionLabel("Ton beim Wecken")
            ToggleRow("Ton", s.soundSource != SoundSource.OFF, { on ->
                vm.updateEditing { it.copy(soundSource = if (on) SoundSource.WAKE_UP else SoundSource.OFF, soundChannel = 1) }
                vm.playCurrentPreview()
            })
            if (s.soundSource != SoundSource.OFF) {
                Spacer(Modifier.height(Space.s))
                Segmented(
                    options = listOf(SoundSource.WAKE_UP to "Weckton", SoundSource.RADIO to "Radio"),
                    selected = s.soundSource,
                    onSelect = { src ->
                        vm.updateEditing { it.copy(soundSource = src, soundChannel = 1) }
                        vm.playCurrentPreview()
                    },
                )
                Spacer(Modifier.height(Space.s))
                ChoiceList(
                    options = if (s.soundSource == SoundSource.WAKE_UP) themes?.wakeSounds ?: listOf("Forest Birds")
                    else (1..5).map { radioLabel(it, presets) },
                    selected = s.soundChannel - 1,
                    onSelect = { i ->
                        vm.updateEditing { it.copy(soundChannel = i + 1) }
                        vm.playCurrentPreview()
                    },
                )
                LabeledSlider("Lautstärke", s.volume, 1..25, format = { "$it von 25" }, onDone = vm::playCurrentPreview) { v ->
                    vm.updateEditing { it.copy(volume = v) }
                }
            }

            if (!editing.isNew) {
                Spacer(Modifier.height(Space.section))
                TextAction("Wecker löschen", { vm.deleteAlarm(s.slot) })
            }
            Spacer(Modifier.height(Space.section))
        }

        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(Space.page)) {
            PrimaryButton("Speichern", vm::saveEditing, Modifier.fillMaxWidth(), pending = Pending.SAVE in pending)
        }
    }

    if (pickTime) TimeDialog(s.hour, s.minute, onDismiss = { pickTime = false }) { h, m ->
        vm.updateEditing { it.copy(hour = h, minute = m) }
        pickTime = false
    }
    if (askDiscard) DiscardDialog(onDiscard = { askDiscard = false; vm.closeEditor() }, onKeep = { askDiscard = false })
}
