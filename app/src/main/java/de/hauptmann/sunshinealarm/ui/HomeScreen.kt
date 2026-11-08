package de.hauptmann.sunshinealarm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import de.hauptmann.sunshinealarm.data.AlarmEntity
import de.hauptmann.sunshinealarm.data.SensorReading
import de.hauptmann.sunshinealarm.data.Themes
import de.hauptmann.sunshinealarm.data.Verdict
import de.hauptmann.sunshinealarm.data.lastNight
import de.hauptmann.sunshinealarm.nap.NapState
import de.hauptmann.sunshinealarm.ui.theme.Line
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.Type
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Home: one main moment (next alarm, or the nap while it runs), two programs to start,
 * and the bedroom in a few figures. Lamp and playback live in "Steuerung".
 */
@Composable
fun HomeScreen(vm: MainViewModel) {
    val next by vm.homeState.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val latest by vm.latest.collectAsStateWithLifecycle()
    val themes by vm.themes.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val deviceName by vm.deviceName.collectAsStateWithLifecycle()
    val sunset by vm.sunset.collectAsStateWithLifecycle()
    val sunsetRemaining by vm.sunsetRemaining.collectAsStateWithLifecycle()
    val sleepWindow by vm.sleepWindow.collectAsStateWithLifecycle()
    val nap by vm.napState.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val atNight = lastNight(now, sleepWindow.first, sleepWindow.second).let { !now.isAfter(it.end) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
        // Device and its state in words; the icon opens the connection settings
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(deviceName ?: "Lichtwecker", style = Type.secondary, color = Palette.textSecondary)
                Text(
                    when (connection) {
                        Connection.Ok -> "verbunden"
                        Connection.Loading -> "lädt"
                        is Connection.Failed -> "nicht erreichbar"
                        Connection.Unknown -> "verbindet"
                    },
                    style = Type.meta,
                    color = Palette.textMuted,
                )
            }
            IconAction(Line.wifi, "Verbindung", vm::openConnection)
        }
        if (connection is Connection.Failed) {
            Spacer(Modifier.height(Space.s))
            Notice("Wecker nicht erreichbar", "Angezeigt wird der letzte bekannte Stand.")
        }

        Spacer(Modifier.height(Space.xl))
        when (val n = nap) {
            is NapState.Running -> NapCountdown(vm, n)
            is NapState.Ringing -> NapRinging(vm, n)
            NapState.Idle -> NextAlarm(vm, next, now, themes)
        }
        if (nap !is NapState.Idle) {
            next?.let { (a, at) ->
                Spacer(Modifier.height(Space.l))
                Text("Nächster Wecker ${hhmm(a.hour, a.minute)} · ${dayLabel(at, now)}", style = Type.secondary, color = Palette.textMuted)
            }
        }

        // ---- Programs: a running sunset shows its state, otherwise two buttons start them ----
        Spacer(Modifier.height(Space.xxl))
        if (sunset?.running == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sonnenuntergang läuft", style = Type.body, color = Palette.text)
                    Text("noch ${(sunsetRemaining + 59) / 60} min", style = Type.value, color = Palette.accent)
                }
                SecondaryButton("Beenden", { vm.setSunsetRunning(false) }, pending = Pending.SUNSET in pending)
            }
            Spacer(Modifier.height(Space.m))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            if (nap is NapState.Idle) SecondaryButton("Nap", vm::openNap, Modifier.weight(1f), icon = Line.alarm)
            if (sunset?.running != true) {
                SecondaryButton(
                    "Sonnenuntergang", { vm.setSunsetRunning(true) }, Modifier.weight(1.7f),
                    icon = Line.moon, pending = Pending.SUNSET in pending,
                )
            }
        }

        // ---- Bedroom ----
        Spacer(Modifier.height(Space.section))
        latest?.let { r -> ClimateBlock(r, atNight) { vm.select(Tab.HISTORY) } }
        Spacer(Modifier.height(Space.section))
    }
}

@Composable
private fun NextAlarm(vm: MainViewModel, next: Pair<AlarmEntity, LocalDateTime>?, now: LocalDateTime, themes: Themes?) {
    Text(next?.first?.name?.let { "Nächster Wecker · $it" } ?: "Nächster Wecker", style = Type.secondary, color = Palette.textMuted)
    Spacer(Modifier.height(Space.s))
    if (next == null) {
        Text("Kein Wecker aktiv", style = Type.title, color = Palette.textSecondary)
        TextAction("Wecker stellen", { vm.select(Tab.ALARMS) })
        return
    }
    val (alarm, at) = next
    Text(hhmm(alarm.hour, alarm.minute), style = Type.hero, color = Palette.text, maxLines = 1)
    Spacer(Modifier.height(Space.xs))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(dayLabel(at, now), style = Type.body, color = Palette.textSecondary, modifier = Modifier.weight(1f))
        Text(untilLabel(at, now), style = Type.value, color = Palette.accent)
    }
    Spacer(Modifier.height(Space.l))
    Horizon(horizonFraction(at, now))
    Spacer(Modifier.height(Space.xxl))
    // The card is the way into the alarm; the chevron says so
    Card(Modifier.clickable(onClickLabel = "Wecker bearbeiten") { vm.edit(alarm.slot) }) {
        Column {
            SunriseCurve(
                minutes = alarm.sunriseMinutes ?: 30,
                level = alarm.sunriseLevel ?: 20,
                theme = alarm.lightTheme ?: 0,
                endHour = alarm.hour,
                endMinute = alarm.minute,
            )
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val themeName = themes?.light?.getOrNull(alarm.lightTheme ?: 0) ?: "Sonnenaufgang"
                Text(
                    "$themeName · ${alarm.sunriseMinutes ?: 30} min · Helligkeit ${alarm.sunriseLevel ?: 20}",
                    style = Type.secondary,
                    color = Palette.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Icon(Line.chevron, null, tint = Palette.textMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** While a nap runs: a live countdown takes the place of the next alarm */
@Composable
private fun NapCountdown(vm: MainViewModel, n: NapState.Running) {
    val clock by vm.clock.collectAsStateWithLifecycle()
    val left = ((n.endsAt - clock) / 1000).coerceAtLeast(0)
    val total = n.plan.minutes * 60f
    val end = Instant.ofEpochMilli(n.endsAt).atZone(ZoneId.systemDefault())
    Text("Nap", style = Type.secondary, color = Palette.textMuted)
    Spacer(Modifier.height(Space.s))
    Text("%02d:%02d".format(left / 60, left % 60), style = Type.hero, color = Palette.text, maxLines = 1)
    Spacer(Modifier.height(Space.xs))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Wecken um ${hhmm(end.hour, end.minute)}", style = Type.body, color = Palette.textSecondary, modifier = Modifier.weight(1f))
        Text("${n.plan.minutes} min", style = Type.value, color = Palette.accent)
    }
    Spacer(Modifier.height(Space.l))
    Horizon(1f - left / total)
    Spacer(Modifier.height(Space.l))
    SecondaryButton("Beenden", vm::stopNap, Modifier.fillMaxWidth())
}

/** The nap is over: say so plainly and offer the one action that matters */
@Composable
private fun NapRinging(vm: MainViewModel, n: NapState.Ringing) {
    val since = Instant.ofEpochMilli(n.since).atZone(ZoneId.systemDefault())
    Text("Nap", style = Type.secondary, color = Palette.textMuted)
    Spacer(Modifier.height(Space.s))
    Text("Vorbei", style = Type.hero, color = Palette.text, maxLines = 1)
    Spacer(Modifier.height(Space.xs))
    Text("seit ${hhmm(since.hour, since.minute)}, Lampe und Ton laufen am Wecker", style = Type.body, color = Palette.accent)
    Spacer(Modifier.height(Space.l))
    Horizon(1f)
    Spacer(Modifier.height(Space.l))
    PrimaryButton("Stoppen", vm::stopNap, Modifier.fillMaxWidth())
}

/** The bedroom as four figures in two rows; at night with a word for anything outside the target */
@Composable
private fun ClimateBlock(r: SensorReading, atNight: Boolean, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Verlauf öffnen", onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Schlafzimmer", style = Type.secondary, color = Palette.textMuted, modifier = Modifier.weight(1f))
            val t = Instant.ofEpochMilli(r.timestamp).atZone(ZoneId.systemDefault())
            Text("gemessen ${hhmm(t.hour, t.minute)}", style = Type.meta, color = Palette.textMuted)
            Icon(Line.chevron, null, tint = Palette.textMuted, modifier = Modifier.padding(start = Space.s).size(18.dp))
        }
        Spacer(Modifier.height(Space.s))
        // Figure on top, its name below; two per row
        Metric.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
                pair.forEach { m ->
                    val v = m.of(r)
                    val digits = if (m == Metric.TEMPERATURE || (m == Metric.LIGHT && v < 100)) 1 else 0
                    Column(Modifier.weight(1f)) {
                        Text("${decimal(v, digits)} ${m.unit}", style = Type.figure.copy(fontSize = Type.value.fontSize * 1.6f), color = Palette.text)
                        Text(m.label, style = Type.secondary, color = Palette.textMuted)
                    }
                }
            }
        }
        if (atNight) {
            val off = Metric.entries.filter { it.target.verdict(it.of(r)) != Verdict.OK }.map { verdictWord(it.target, it.of(r)) }
            Spacer(Modifier.height(Space.s))
            Text(
                if (off.isEmpty()) "Alles im Zielbereich für die Nacht" else off.joinToString(", ").replaceFirstChar { it.uppercase() },
                style = Type.secondary,
                color = Palette.textSecondary,
            )
        }
    }
}
