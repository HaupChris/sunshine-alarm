package de.hauptmann.sunshinealarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hauptmann.sunshinealarm.data.DARK_LUX
import de.hauptmann.sunshinealarm.data.SensorReading
import de.hauptmann.sunshinealarm.data.SleepNight
import de.hauptmann.sunshinealarm.data.lastNight
import de.hauptmann.sunshinealarm.data.nightsBetween
import de.hauptmann.sunshinealarm.data.summarize
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val readings by vm.history.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val metric by vm.metric.collectAsStateWithLifecycle()
    val total by vm.readingCount.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val window by vm.sleepWindow.collectAsStateWithLifecycle()
    var pick by remember { mutableStateOf<String?>(null) }
    val nights by vm.nights.collectAsStateWithLifecycle()
    var editNight by remember { mutableStateOf<SleepNight?>(null) }
    val view by vm.historyView.collectAsStateWithLifecycle()
    val hints by vm.hints.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
        ScreenTitle("Verlauf")
        Segmented(listOf(HistoryView.NIGHTS to "Nächte", HistoryView.CLIMATE to "Raumklima"), view, vm::setHistoryView)
        Spacer(Modifier.height(Space.xxl))

        if (view == HistoryView.NIGHTS) {
            // ---- Last night against the targets ----
            val fallback = lastNight(now, window.first, window.second)
            // Prefer the tracked sleep of that night over the default window
            val tracked = nights.firstOrNull { it.night == fallback.start.toLocalDate().toString() }
                ?.takeIf { it.effectiveStart != null && it.effectiveEnd != null }
            val from = tracked?.effectiveStart ?: fallback.startMillis()
            val to = tracked?.effectiveEnd ?: fallback.endMillis()
            val nightReadings = readings.filter { it.timestamp in from..to }
            Row(verticalAlignment = Alignment.Bottom) {
                SectionLabel("Letzte Nacht", Modifier.weight(1f))
                Text(
                    "${clockLabel(from)} – ${clockLabel(to)}" + if (tracked != null) "" else " · Standard",
                    style = Type.meta,
                    color = Palette.textMuted,
                    modifier = Modifier.padding(bottom = Space.s),
                )
            }
            if (nightReadings.isEmpty()) {
                Hint("Für diese Nacht gibt es noch keine Messungen")
            } else {
                Metric.entries.forEach { m ->
                    val sum = summarize(nightReadings.map { m.of(it) }, m.target) ?: return@forEach
                    NightRow(m, sum.average, sum.shareInRange)
                }
                Text("${nightReadings.size} Messungen in der Schlafzeit", style = Type.meta, color = Palette.textMuted,
                    modifier = Modifier.padding(top = Space.xs))
            }

            // ---- Sleep times, detected from the light ----
            Spacer(Modifier.height(Space.section))
            SectionLabel("Schlafzeiten")
            Hint("Erkannt am Licht: Der Schlaf beginnt, wenn es dunkel wird (unter ${DARK_LUX.toInt()} lx), und endet, wenn es hell wird.")
            Spacer(Modifier.height(Space.s))
            val shown = nights.filter { it.effectiveStart != null && it.effectiveEnd != null }.take(7)
            if (shown.isEmpty()) {
                Text("Noch keine Nacht erkannt. Dafür braucht es Messungen über die ganze Nacht.", style = Type.secondary, color = Palette.textSecondary)
            }
            shown.forEach { n -> SleepRow(n) { editNight = n } }

            Spacer(Modifier.height(Space.section))
            SectionLabel("Standard-Schlafzeit, wenn nichts erkannt wurde")
            NavRow("Schlafenszeit", value = minutesLabel(window.first)) { pick = "bed" }
            NavRow("Aufstehen", value = minutesLabel(window.second)) { pick = "wake" }
            Spacer(Modifier.height(Space.l))
            Hint("Zielwerte: 16–19 °C und 40–60 % Luftfeuchte (Umweltbundesamt, Schlafratgeber), bis 30 dB (WHO), möglichst unter 1 lx.")
        } else {
            // ---- The chart ----
            Segmented(HistoryRange.entries.map { it to it.label }, range, vm::setRange)
            Spacer(Modifier.height(Space.m))
            Segmented(Metric.entries.map { it to it.label }, metric, vm::setMetric, compact = true)
            Spacer(Modifier.height(Space.xxl))

            if (readings.size < 2) {
                Text("Noch zu wenige Messungen für diesen Zeitraum", style = Type.body, color = Palette.textSecondary)
                Spacer(Modifier.height(Space.s))
                Hint("Die App speichert alle 15 Minuten einen Wert, solange das Handy im WLAN ist.")
            } else {
                val values = readings.map { metric.of(it) }
                val digits = digitsFor(metric)
                Row {
                    Text(decimal(values.last(), digits), style = Type.figure, color = Palette.text)
                    Text(" ${metric.unit}", style = Type.figure, color = Palette.textMuted)
                }
                Text("zuletzt gemessen · Ziel nachts ${targetLabel(metric.target, metric.unit)}", style = Type.secondary, color = Palette.textMuted)
                Spacer(Modifier.height(Space.xl))
                Card(padding = Space.m) { LineChart(readings, metric, range, window, onZoomed = { vm.hintSeen(HINT_ZOOM) }) }
                Spacer(Modifier.height(Space.s))
                Hint(
                    (if (HINT_ZOOM !in hints) "Mit zwei Fingern zoomen, doppelt tippen setzt zurück. " else "") +
                        "Hell hinterlegt: Schlafzeit und Zielbereich.",
                )
                Spacer(Modifier.height(Space.xxl))
                ValueRow("Minimum", "${decimal(values.min(), digits)} ${metric.unit}")
                ValueRow("Maximum", "${decimal(values.max(), digits)} ${metric.unit}")
                ValueRow("Durchschnitt", "${decimal(values.average(), digits)} ${metric.unit}")
                ValueRow("Messungen", "${readings.size}")
            }
            Spacer(Modifier.height(Space.xl))
            Text("$total Messungen insgesamt auf diesem Handy", style = Type.meta, color = Palette.textMuted)
        }
        Spacer(Modifier.height(Space.section))
    }

    editNight?.let { n ->
        NightDialog(n, onDismiss = { editNight = null }) { start, end ->
            vm.adjustNight(n.night, start, end)
            editNight = null
        }
    }

    pick?.let { which ->
        val current = if (which == "bed") window.first else window.second
        TimeDialog(current / 60, current % 60, onDismiss = { pick = null }) { h, m ->
            if (which == "bed") vm.setSleepWindow(h * 60 + m, window.second) else vm.setSleepWindow(window.first, h * 60 + m)
            pick = null
        }
    }
}

private fun clockLabel(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).let { hhmm(it.hour, it.minute) }

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EE d.M.", java.util.Locale.GERMAN)

/** One night: date, times, duration as a thin bar; "korrigiert" when edited by hand */
@Composable
private fun SleepRow(n: SleepNight, onClick: () -> Unit) {
    val start = n.effectiveStart!!
    val end = n.effectiveEnd!!
    val minutes = ((end - start) / 60_000).toInt()
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(n.date.format(DAY_FORMAT), style = Type.body, color = Palette.textSecondary, modifier = Modifier.weight(1f))
            Text("${clockLabel(start)} – ${clockLabel(end)}", style = Type.value, color = Palette.text)
        }
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.raised)) {
                Box(
                    Modifier.fillMaxWidth((minutes / 600f).coerceIn(0f, 1f)).height(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(Palette.textSecondary),
                )
            }
            Text(
                "${minutes / 60} h ${minutes % 60} min" + if (n.adjusted) " · korrigiert" else "",
                style = Type.meta,
                color = Palette.textMuted,
                modifier = Modifier.padding(start = Space.m),
            )
        }
    }
}

/** Correct the times of one night; "Erkannt" restores what the light showed */
@Composable
private fun NightDialog(n: SleepNight, onDismiss: () -> Unit, onSave: (Long?, Long?) -> Unit) {
    val zone = ZoneId.systemDefault()
    var start by remember { mutableStateOf(n.effectiveStart) }
    var end by remember { mutableStateOf(n.effectiveEnd) }
    var picking by remember { mutableStateOf<String?>(null) }
    fun toMillis(h: Int, m: Int, evening: Boolean): Long {
        // Times after noon belong to the evening of the night's date, earlier ones to the next morning
        val day = if (evening && h >= 12) n.date else n.date.plusDays(if (h < 12 || !evening) 1 else 0)
        return day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.card,
        title = { Text("Nacht vom ${n.date.format(DAY_FORMAT)}", style = Type.section, color = Palette.text) },
        text = {
            Column {
                ValueRow("Eingeschlafen", start?.let(::clockLabel) ?: "–", Modifier.clickable { picking = "start" })
                ValueRow("Aufgewacht", end?.let(::clockLabel) ?: "–", Modifier.clickable { picking = "end" })
                if (n.detectedStart != null && n.detectedEnd != null) {
                    Spacer(Modifier.height(Space.s))
                    Text("Am Licht erkannt: ${clockLabel(n.detectedStart)} – ${clockLabel(n.detectedEnd)}", style = Type.meta, color = Palette.textMuted)
                }
            }
        },
        confirmButton = {
            TextAction("Speichern", {
                val changedStart = start.takeIf { it != n.detectedStart }
                val changedEnd = end.takeIf { it != n.detectedEnd }
                onSave(changedStart, changedEnd)
            }, color = Palette.text)
        },
        dismissButton = {
            Row {
                if (n.adjusted) TextAction("Erkannt verwenden", { onSave(null, null) })
                TextAction("Abbrechen", onDismiss)
            }
        },
    )
    picking?.let { which ->
        val current = (if (which == "start") start else end)?.let { Instant.ofEpochMilli(it).atZone(zone) }
        TimeDialog(current?.hour ?: 23, current?.minute ?: 0, onDismiss = { picking = null }) { h, m ->
            if (which == "start") start = toMillis(h, m, evening = true) else end = toMillis(h, m, evening = false)
            picking = null
        }
    }
}

private const val HINT_ZOOM = "chart_zoom"

private fun digitsFor(m: Metric) = if (m == Metric.TEMPERATURE || m == Metric.LIGHT) 1 else 0

/** One metric of last night: average, share of time in the target range as a thin bar */
@Composable
private fun NightRow(m: Metric, average: Double, share: Double) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.label, style = Type.body, color = Palette.textSecondary, modifier = Modifier.weight(1f))
            Text("Ø ${decimal(average, digitsFor(m))} ${m.unit}", style = Type.value, color = Palette.text)
        }
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.raised)) {
                Box(Modifier.fillMaxWidth(share.toFloat()).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.textSecondary))
            }
            Text(
                "${(share * 100).roundToInt()} % im Ziel · ${verdictWord(m.target, average)}",
                style = Type.meta,
                color = Palette.textMuted,
                modifier = Modifier.padding(start = Space.m),
            )
        }
    }
}

/**
 * The one chart: a single amber line on a muted grid with mono labels.
 * Sleep windows and the target band are light neutral areas behind it.
 * Pinch to zoom the time axis, drag to pan, double tap to reset.
 */
@Composable
private fun LineChart(readings: List<SensorReading>, metric: Metric, range: HistoryRange, window: Pair<Int, Int>, onZoomed: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val zone = ZoneId.systemDefault()
    val labelStyle = Type.meta.copy(color = Palette.textMuted)
    var zoom by remember(range) { mutableFloatStateOf(1f) }
    var pan by remember(range) { mutableFloatStateOf(0f) } // 0..1, start of the visible part

    val t0 = readings.first().timestamp
    val t1 = maxOf(readings.last().timestamp, t0 + 1)
    val span = (t1 - t0) / zoom
    val visStart = t0 + ((t1 - t0) - span) * pan
    val visEnd = visStart + span
    val visibleSpanHours = span / 3_600_000.0
    val timeFormat = DateTimeFormatter.ofPattern(if (visibleSpanHours <= 36) "HH:mm" else "dd.MM.")

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(range) {
                detectTapGestures(onDoubleTap = { zoom = 1f; pan = 0f })
            }
            .pointerInput(range) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val w = size.width.toFloat()
                    val oldZoom = zoom
                    val newZoom = (zoom * zoomChange).coerceIn(1f, 48f)
                    // keep the point under the fingers in place while zooming
                    val focus = (pan * (1 - 1 / oldZoom) + centroid.x / w / oldZoom)
                    var newPan = if (newZoom > 1f) (focus - centroid.x / w / newZoom) / (1 - 1 / newZoom) else 0f
                    if (newZoom > 1f) newPan -= panChange.x / w / newZoom / (1 - 1 / newZoom)
                    zoom = newZoom
                    pan = newPan.coerceIn(0f, 1f)
                    if (newZoom > 1.2f) onZoomed()
                }
            },
    ) {
        val visible = readings.filter { it.timestamp.toDouble() in visStart..visEnd }.ifEmpty { readings }
        val values = visible.map { metric.of(it) }
        val target = metric.target
        var lo = values.min()
        var hi = values.max()
        // Show the target band when it is near the data, so the relation is visible
        target.low?.let { if (it > lo - (hi - lo + 1) * 2) lo = minOf(lo, it) }
        target.high?.let { if (it < hi + (hi - lo + 1) * 2) hi = maxOf(hi, it) }
        if (hi - lo < 1.0) { lo -= 0.5; hi += 0.5 }
        val pad = (hi - lo) * 0.1
        lo -= pad; hi += pad

        val labelW = 44.dp.toPx()
        val labelH = 20.dp.toPx()
        val w = size.width - labelW
        val h = size.height - labelH
        fun x(t: Long) = ((t - visStart) / (visEnd - visStart) * w).toFloat()
        fun y(v: Double) = (h - (v - lo) / (hi - lo) * h).toFloat()

        clipRect(0f, 0f, w, h) {
            // Sleep windows
            nightsBetween(visStart.toLong(), visEnd.toLong(), window.first, window.second).forEach { n ->
                val nx0 = x(n.startMillis()).coerceIn(0f, w)
                val nx1 = x(n.endMillis()).coerceIn(0f, w)
                if (nx1 > nx0) drawRect(Palette.text.copy(alpha = 0.04f), Offset(nx0, 0f), Size(nx1 - nx0, h))
            }
            // Target band with dashed edges
            val bandTop = y(target.high ?: hi).coerceIn(0f, h)
            val bandBottom = y(target.low ?: lo).coerceIn(0f, h)
            if (bandBottom > bandTop) {
                drawRect(Palette.text.copy(alpha = 0.05f), Offset(0f, bandTop), Size(w, bandBottom - bandTop))
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                target.high?.let { drawLine(Palette.textMuted, Offset(0f, y(it)), Offset(w, y(it)), 1.dp.toPx(), pathEffect = dash) }
                target.low?.let { drawLine(Palette.textMuted, Offset(0f, y(it)), Offset(w, y(it)), 1.dp.toPx(), pathEffect = dash) }
            }
        }

        // Grid: three horizontal lines with values on the right
        for (i in 0..2) {
            val v = lo + (hi - lo) * (0.15 + 0.35 * i)
            val gy = y(v)
            drawLine(Palette.hairline.copy(alpha = 0.10f), Offset(0f, gy), Offset(w, gy), 1.dp.toPx())
            val label = measurer.measure(decimal(v, if (metric == Metric.TEMPERATURE) 1 else 0), labelStyle)
            drawText(label, topLeft = Offset(w + 8.dp.toPx(), gy - label.size.height / 2f))
        }

        clipRect(0f, 0f, w, size.height) {
            val line = Path()
            readings.forEachIndexed { i, r ->
                val px = x(r.timestamp); val py = y(metric.of(r))
                if (i == 0) line.moveTo(px, py) else line.lineTo(px, py)
            }
            val area = Path().apply {
                addPath(line)
                lineTo(x(readings.last().timestamp), h)
                lineTo(x(readings.first().timestamp), h)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(Palette.accent.copy(alpha = 0.16f), Palette.accent.copy(alpha = 0f)), endY = h))
            drawPath(line, Palette.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (zoom > 6f) visible.forEach { r -> drawCircle(Palette.accent, 2.5.dp.toPx(), Offset(x(r.timestamp), y(metric.of(r)))) }
            val last = readings.last()
            drawCircle(Palette.accent, 3.5.dp.toPx(), Offset(x(last.timestamp), y(metric.of(last))))
        }

        // Time labels at start, middle and end of the visible part
        listOf(0f, 0.5f, 1f).forEach { f ->
            val t = (visStart + (visEnd - visStart) * f).toLong()
            val text = Instant.ofEpochMilli(t).atZone(zone).format(timeFormat)
            val label = measurer.measure(text, labelStyle)
            val lx = (w * f - label.size.width * f).coerceIn(0f, w - label.size.width)
            drawText(label, topLeft = Offset(lx, h + 6.dp.toPx()))
        }
    }
}
