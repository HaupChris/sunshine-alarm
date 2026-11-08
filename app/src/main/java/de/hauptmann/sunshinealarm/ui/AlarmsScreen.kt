package de.hauptmann.sunshinealarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hauptmann.sunshinealarm.api.SoundSource
import de.hauptmann.sunshinealarm.data.AlarmEntity
import de.hauptmann.sunshinealarm.data.Themes
import de.hauptmann.sunshinealarm.ui.theme.Line
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Radius
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.Type

private const val HINT_LONG_PRESS = "long_press_delete"

@Composable
fun AlarmsScreen(vm: MainViewModel) {
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val themes by vm.themes.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val hints by vm.hints.collectAsStateWithLifecycle()
    val inUse = alarms.filter { it.inUse }
    // Long press marks a row; it then shows a delete button instead of its switch
    var marked by remember { mutableStateOf<Int?>(null) }
    BackHandler(enabled = marked != null) { marked = null }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = Space.page)) {
        item { ScreenTitle("Wecker") }
        if (inUse.isEmpty()) {
            item { Text("Noch kein Wecker gestellt", style = Type.body, color = Palette.textSecondary) }
        }
        items(inUse, key = { it.slot }) { a ->
            AlarmRow(
                a, alarmSummary(a, themes, presets),
                marked = marked == a.slot,
                pending = Pending.alarm(a.slot) in pending,
                onToggle = { vm.setAlarmEnabled(a.slot, it) },
                onOpen = { if (marked != null) marked = null else vm.edit(a.slot) },
                onMark = {
                    marked = a.slot
                    vm.hintSeen(HINT_LONG_PRESS)
                },
                onDelete = {
                    marked = null
                    vm.deleteAlarm(a.slot)
                },
            )
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(Palette.hairline))
        }
        if (inUse.isNotEmpty() && HINT_LONG_PRESS !in hints) {
            item { Hint("Lange drücken, um einen Wecker zu löschen", Modifier.padding(top = Space.m)) }
        }
        item {
            Spacer(Modifier.height(Space.xxl))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (inUse.size < 16) SecondaryButton("Wecker", vm::newAlarm, Modifier.weight(1f), icon = Line.plus)
                SecondaryButton("Nap", vm::openNap, Modifier.weight(1f), icon = Line.alarm)
            }
            Spacer(Modifier.height(Space.section))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmRow(
    a: AlarmEntity,
    summary: String,
    marked: Boolean,
    pending: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onMark: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.control))
            .background(if (marked) Palette.raised else Color.Transparent)
            .combinedClickable(
                onClick = onOpen,
                onClickLabel = "Wecker bearbeiten",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMark()
                },
                onLongClickLabel = "Löschen anbieten",
            )
            .padding(vertical = Space.l, horizontal = if (marked) Space.m else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            (a.name ?: if (a.napEndsAt != null) "Nap" else null)?.let {
                Text(it, style = Type.secondary, color = Palette.textSecondary)
            }
            Text(hhmm(a.hour, a.minute), style = Type.figure, color = if (a.enabled) Palette.text else Palette.textMuted)
            Spacer(Modifier.height(Space.xs))
            Text(summary, style = Type.secondary, color = Palette.textMuted)
        }
        if (marked) {
            IconAction(Line.trash, "Wecker ${hhmm(a.hour, a.minute)} löschen", onDelete, tint = Palette.text, active = true)
        } else {
            Column(horizontalAlignment = Alignment.End) {
                AppSwitch(a.enabled, onToggle, pending = pending)
                Text(if (a.enabled) "an" else "aus", style = Type.meta, color = Palette.textMuted)
            }
        }
    }
}

private fun alarmSummary(a: AlarmEntity, themes: Themes?, presets: List<RadioPreset>): String {
    val parts = mutableListOf(if (a.napEndsAt != null) "Einmalig" else daysLabel(a.dayMask))
    a.sunriseMinutes?.let { parts += "$it min Licht" }
    when (a.soundSource) {
        SoundSource.WAKE_UP.code -> themes?.wakeSounds?.getOrNull((a.soundChannel ?: 1) - 1)?.let { parts += it }
        SoundSource.RADIO.code -> parts += presets.firstOrNull { it.number == (a.soundChannel ?: 1) }?.label ?: "Radio"
        SoundSource.OFF.code -> parts += "ohne Ton"
    }
    return parts.joinToString(" · ")
}
