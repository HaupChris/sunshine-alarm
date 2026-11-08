package de.hauptmann.sunshinealarm.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.hauptmann.sunshinealarm.api.Weekday
import de.hauptmann.sunshinealarm.data.Target
import de.hauptmann.sunshinealarm.data.Verdict
import de.hauptmann.sunshinealarm.ui.theme.Line
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Radius
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.Type
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Interaction rules of the app (see the UX review):
 *  - Tapping a row opens its details; a chevron shows it leads somewhere.
 *    The element at the right end is a quick action (switch, play).
 *  - Switch = something is on or off. Button Starten/Beenden = a program with a duration.
 *    Segmented = choose a variant; it never contains "Aus".
 *  - Selection = check mark and primary text. Amber only marks what runs right now or time.
 *  - Device states apply at once; editors apply with "Speichern" at the bottom and ask
 *    before discarding changes.
 *  - Deleting happens at once and can be undone from the message at the bottom.
 *  - While something is being sent, the element itself shows it.
 */

// ---------- Formatting ----------

private val GERMAN = Locale.GERMAN
private val SHORT_DAYS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

fun weekdayShort(day: Weekday) = SHORT_DAYS[day.ordinal]

fun daysLabel(mask: Int): String = when (mask) {
    0 -> "Einmalig"
    Weekday.EVERY_DAY -> "Täglich"
    Weekday.WORKDAYS -> "Mo – Fr"
    Weekday.WEEKEND -> "Sa, So"
    else -> Weekday.fromMask(mask).sortedBy { it.ordinal }.joinToString(", ") { weekdayShort(it) }
}

fun hhmm(hour: Int, minute: Int) = "%02d:%02d".format(hour, minute)

fun minutesLabel(totalMinutes: Int): String {
    val m = Math.floorMod(totalMinutes, 24 * 60)
    return hhmm(m / 60, m % 60)
}

fun dayLabel(at: LocalDateTime, now: LocalDateTime): String {
    val weekday = at.dayOfWeek.getDisplayName(TextStyle.FULL, GERMAN)
    return when (at.toLocalDate()) {
        now.toLocalDate() -> "Heute"
        now.toLocalDate().plusDays(1) -> "Morgen · $weekday"
        else -> weekday
    }
}

fun untilLabel(at: LocalDateTime, now: LocalDateTime): String {
    val minutes = Duration.between(now, at).toMinutes().coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "in $m min"
        m == 0L -> "in $h h"
        else -> "in $h h $m min"
    }
}

fun decimal(value: Double, digits: Int = 1) = String.format(GERMAN, "%.${digits}f", value)

/** Status word for a measured value against its sleep target */
fun verdictWord(target: Target, v: Double): String = when (target.verdict(v)) {
    Verdict.OK -> "im Zielbereich"
    Verdict.LOW -> when (target) {
        Target.TEMPERATURE -> "zu kühl"
        Target.HUMIDITY -> "zu trocken"
        else -> "zu niedrig"
    }
    Verdict.HIGH -> when (target) {
        Target.TEMPERATURE -> "zu warm"
        Target.HUMIDITY -> "zu feucht"
        Target.NOISE -> "zu laut"
        Target.LIGHT -> "zu hell"
    }
}

fun targetLabel(t: Target, unit: String): String = when {
    t.low != null && t.high != null -> "${decimal(t.low, 0)} – ${decimal(t.high, 0)} $unit"
    t.high != null -> "bis ${decimal(t.high, 0)} $unit"
    else -> "ab ${decimal(t.low!!, 0)} $unit"
}

// ---------- Signature: the horizon ----------

/** How far the sun has travelled towards the next alarm, over the last 12 hours before it */
fun horizonFraction(at: LocalDateTime, now: LocalDateTime): Float {
    val window = Duration.ofHours(12).toMinutes().toFloat()
    val left = Duration.between(now, at).toMinutes().toFloat()
    return (1f - left / window).coerceIn(0f, 1f)
}

/**
 * The signature detail: a hairline horizon with a small sun that rises towards the
 * right edge as the alarm comes closer. Used only for the next alarm and the nap.
 */
@Composable
fun Horizon(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        val y = size.height / 2
        val x = size.width * fraction
        drawLine(Palette.hairline.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        drawLine(Palette.textMuted, Offset(0f, y), Offset(x, y), 1.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Palette.background, radius = 7.dp.toPx(), center = Offset(x, y))
        drawCircle(Palette.accent, radius = 4.dp.toPx(), center = Offset(x, y))
    }
}

// ---------- Moment: the sunrise / sunset curve ----------

/** Approximate colors of the four Somneo light themes, from night to full light */
fun themeColors(theme: Int): List<Color> = when (theme) {
    1 -> listOf(0xFF1B1D2A, 0xFF5E1C24, 0xFFB83A2E, 0xFFE8774A, 0xFFF7C79C) // Island red
    2 -> listOf(0xFF1B1D2A, 0xFF34405A, 0xFF7F92B2, 0xFFCDD6E4, 0xFFF4F6FA) // Nordic white
    3 -> listOf(0xFF1B1D2A, 0xFF5C1E3A, 0xFFB9404F, 0xFFEC8B6A, 0xFFFAD6B9) // Caribbean red
    else -> listOf(0xFF1B1D2A, 0xFF6E2A1E, 0xFFD0612B, 0xFFF2B04A, 0xFFFBE8B8) // Sunny day
}.map { Color(it) }

/** Brightness 1..25 the device shows at position [f] (0..1) of a sunrise or sunset */
fun curveLevel(level: Int, f: Float, descending: Boolean): Int {
    val shape = if (descending) (1f - f).pow(1.8f) else f.pow(1.8f)
    return (level * shape).roundToInt().coerceIn(1, 25)
}

/**
 * The light rising before the alarm (or falling for a sunset) in the colors of the theme.
 * Height follows the chosen brightness. With [onScrub], a bar can be dragged or set by
 * tapping; screen readers can set it as a progress value.
 */
@Composable
fun SunriseCurve(
    minutes: Int,
    level: Int,
    theme: Int,
    endHour: Int,
    endMinute: Int,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    /** Sunset: light falls from the start time to darkness; endHour/endMinute is then the start */
    descending: Boolean = false,
    startLabel: String? = null,
    scrub: Float? = null,
    onScrub: ((Float) -> Unit)? = null,
) {
    val colors = themeColors(theme).let { if (descending) it.reversed() else it }
    val startTotal = endHour * 60 + endMinute - minutes
    val endTotal = endHour * 60 + endMinute + minutes
    Column(modifier) {
        if (scrub != null) {
            // Sunrise runs from startTotal to the alarm; a sunset from its start (endHour:endMinute) onwards
            val at = (if (descending) endHour * 60 + endMinute else startTotal) + (minutes * scrub).toInt()
            Row(Modifier.fillMaxWidth().padding(bottom = Space.s)) {
                Text(minutesLabel(at), style = Type.meta, color = Palette.text)
                Spacer(Modifier.weight(1f))
                Text("Helligkeit ${curveLevel(level, scrub, descending)} von 25", style = Type.meta, color = Palette.textSecondary)
            }
        }
        val interaction = if (onScrub != null && scrub != null) {
            Modifier
                .pointerInput(Unit) { detectTapGestures { onScrub((it.x / size.width).coerceIn(0f, 1f)) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ -> onScrub((change.position.x / size.width).coerceIn(0f, 1f)) }
                }
                .semantics {
                    contentDescription = "Zeitpunkt in der Lichtkurve"
                    progressBarRangeInfo = ProgressBarRangeInfo(scrub, 0f..1f)
                    setProgress { onScrub(it.coerceIn(0f, 1f)); true }
                }
        } else Modifier
        Canvas(Modifier.fillMaxWidth().height(height).then(interaction)) {
            val w = size.width
            val h = size.height
            val peak = h * (0.25f + 0.75f * level.coerceIn(1, 25) / 25f)
            val steps = 48
            val path = Path().apply {
                moveTo(0f, h)
                for (i in 0..steps) {
                    val t = i / steps.toFloat()
                    val f = if (descending) (1f - t).pow(1.8f) else t.pow(1.8f)
                    lineTo(w * t, h - peak * f)
                }
                lineTo(w, h)
                close()
            }
            drawPath(path, Brush.horizontalGradient(colors))
            drawLine(Palette.hairline.copy(alpha = 0.16f), Offset(0f, h - 0.5f), Offset(w, h - 0.5f), 1.dp.toPx())
            scrub?.let { f ->
                val x = w * f
                val shape = if (descending) (1f - f).pow(1.8f) else f.pow(1.8f)
                val y = h - peak * shape
                drawLine(Palette.text, Offset(x, 0f), Offset(x, h), 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(Palette.background, 8.dp.toPx(), Offset(x, y))
                drawCircle(Palette.text, 5.dp.toPx(), Offset(x, y))
            }
        }
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth()) {
            if (descending) {
                Text(startLabel ?: hhmm(endHour, endMinute), style = Type.meta, color = Palette.textMuted)
                Spacer(Modifier.weight(1f))
                Text(minutesLabel(endTotal), style = Type.meta, color = Palette.textMuted)
            } else {
                Text(minutesLabel(startTotal), style = Type.meta, color = Palette.textMuted)
                Spacer(Modifier.weight(1f))
                Text(hhmm(endHour, endMinute), style = Type.meta, color = Palette.textMuted)
            }
        }
    }
}

// ---------- Surfaces and text ----------

@Composable
fun Card(modifier: Modifier = Modifier, padding: Dp = Space.l, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(Palette.card)
            .border(1.dp, Palette.hairline, RoundedCornerShape(Radius.card))
            .padding(padding),
    ) { content() }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.secondary, color = Palette.textMuted, modifier = modifier.padding(bottom = Space.s))
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.title, color = Palette.text, modifier = modifier.padding(top = Space.xxl, bottom = Space.xl))
}

/** Short muted hint, e.g. for a gesture; shown until the gesture was used once */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.secondary, color = Palette.textMuted, modifier = modifier)
}

// ---------- Rows: tap opens, the right end is a quick action ----------

/** A row that opens details: label, optional value or detail, chevron */
@Composable
fun NavRow(label: String, value: String? = null, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.row).clickable(onClick = onClick).padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.body, color = Palette.text)
            detail?.let { Text(it, style = Type.secondary, color = Palette.textMuted) }
        }
        value?.let { Text(it, style = Type.value, color = Palette.textSecondary, modifier = Modifier.padding(start = Space.s)) }
        Icon(Line.chevron, null, tint = Palette.textMuted, modifier = Modifier.padding(start = Space.s).size(18.dp))
    }
}

/** On/off. The whole row toggles; while sending, the switch is replaced by a small spinner */
@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, detail: String? = null, pending: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Space.row)
            .clickable(enabled = !pending) { onChange(!checked) }
            .semantics { stateDescription = if (checked) "an" else "aus" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.body, color = Palette.text)
            detail?.let { Text(it, style = Type.secondary, color = Palette.textMuted) }
        }
        AppSwitch(checked, onChange, pending = pending)
    }
}

@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit, pending: Boolean = false) {
    Box(contentAlignment = Alignment.Center) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = !pending,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.onAccent,
                checkedTrackColor = Palette.accent,
                checkedBorderColor = Palette.accent,
                uncheckedThumbColor = Palette.textMuted,
                uncheckedTrackColor = Palette.raised,
                uncheckedBorderColor = Palette.hairline,
                disabledCheckedTrackColor = Palette.accent.copy(alpha = 0.4f),
                disabledCheckedThumbColor = Palette.onAccent,
                disabledUncheckedTrackColor = Palette.raised,
                disabledUncheckedThumbColor = Palette.textMuted,
            ),
        )
        if (pending) Spinner()
    }
}

@Composable
fun Spinner(size: Dp = 16.dp) {
    CircularProgressIndicator(Modifier.size(size), color = Palette.text, strokeWidth = 2.dp, trackColor = Color.Transparent)
}

@Composable
fun ValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.body, color = Palette.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = Type.value, color = Palette.text, textAlign = TextAlign.End)
    }
}

// ---------- Choosing ----------

/** A list of options; the chosen one has a check mark and primary text */
@Composable
fun ChoiceList(options: List<String>, selected: Int, onSelect: (Int) -> Unit, leading: (@Composable (Int) -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, name ->
            val on = i == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { onSelect(i) }
                    .semantics { stateDescription = if (on) "ausgewählt" else "" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading?.let { it(i); Spacer(Modifier.width(Space.m)) }
                Text(name, style = Type.body, color = if (on) Palette.text else Palette.textSecondary, modifier = Modifier.weight(1f))
                if (on) Icon(Line.check, null, tint = Palette.text, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Mutually exclusive variants; the selected one sits on a raised surface. Never contains "Aus". */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.control))
            .background(Palette.deepest)
            .border(1.dp, Palette.hairline, RoundedCornerShape(Radius.control))
            .padding(Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            val bg by animateColorAsState(if (active) Palette.raised else Color.Transparent, tween(150), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(Radius.control - Space.xs))
                    .background(bg)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = if (compact) Type.secondary else Type.button,
                    color = if (active) Palette.text else Palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A row of chips for choosing one or several values (days, durations); same look as selection */
@Composable
fun ChipRow(labels: List<String>, isOn: (Int) -> Boolean, onToggle: (Int) -> Unit, round: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        labels.forEachIndexed { i, label ->
            val on = isOn(i)
            val shape = if (round) CircleShape else RoundedCornerShape(Radius.control)
            Box(
                Modifier
                    .weight(1f)
                    .then(if (round) Modifier.aspectRatio(1f) else Modifier.height(48.dp))
                    .clip(shape)
                    .background(if (on) Palette.primaryFill else Color.Transparent)
                    .border(1.dp, if (on) Palette.primaryFill else Palette.hairline, shape)
                    .clickable { onToggle(i) }
                    .semantics { stateDescription = if (on) "ausgewählt" else "" },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.button, color = if (on) Palette.background else Palette.textMuted)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabeledSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    format: (Int) -> String = { it.toString() },
    onDone: () -> Unit = {},
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.body, color = Palette.textSecondary, modifier = Modifier.weight(1f))
            Text(format(value), style = Type.value, color = Palette.text)
        }
        // Neutral track: amber is reserved for what runs right now
        val colors = SliderDefaults.colors(
            thumbColor = Palette.text,
            activeTrackColor = Palette.textSecondary,
            inactiveTrackColor = Palette.raised,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / step).toInt() * step) },
            onValueChangeFinished = onDone,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            colors = colors,
            thumb = { Box(Modifier.size(20.dp).clip(CircleShape).background(Palette.text)) },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = colors,
                    modifier = Modifier.height(4.dp),
                    drawStopIndicator = null,
                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 2.dp,
                )
            },
        )
    }
}

// ---------- Actions ----------

/** The one main action of a screen. Slightly dimmed off-white, so it does not glare at night */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, pending: Boolean = false) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(Radius.control))
            .background(if (enabled) Palette.primaryFill else Palette.raised)
            .clickable(enabled = enabled && !pending, onClick = onClick)
            .padding(horizontal = Space.xl),
        contentAlignment = Alignment.Center,
    ) {
        if (pending) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Palette.background, strokeWidth = 2.dp, trackColor = Color.Transparent)
        } else {
            Text(text, style = Type.button, color = if (enabled) Palette.background else Palette.textMuted, maxLines = 1)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    pending: Boolean = false,
) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(Radius.control))
            .background(Palette.raised)
            .border(1.dp, Palette.hairline, RoundedCornerShape(Radius.control))
            .clickable(enabled = !pending, onClick = onClick)
            .padding(horizontal = Space.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (pending) {
            Spinner(18.dp)
        } else {
            icon?.let {
                Icon(it, null, tint = Palette.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.s))
            }
            Text(text, style = Type.button, color = Palette.text, maxLines = 1)
        }
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, color: Color = Palette.textSecondary) {
    Box(Modifier.height(48.dp).clickable(onClick = onClick).padding(horizontal = Space.xs), contentAlignment = Alignment.CenterStart) {
        Text(text, style = Type.button, color = color)
    }
}

/** Round icon button with a 48dp touch target */
@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Palette.textSecondary, active: Boolean = false) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) Palette.raised else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (active) Palette.text else tint, modifier = Modifier.size(20.dp))
    }
}

/** Quiet notice for a lasting state like "not reachable": icon and words, no red */
@Composable
fun Notice(title: String, detail: String?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.control))
            .background(Palette.raised)
            .padding(Space.m),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Line.alert, null, tint = Palette.textSecondary, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Spacer(Modifier.width(Space.s))
        Column {
            Text(title, style = Type.secondary, color = Palette.text)
            detail?.let { Text(it, style = Type.secondary, color = Palette.textMuted) }
        }
    }
}

/** The message bar at the bottom: one line, optionally one action */
@Composable
fun MessageBar(message: UiMessage, onAction: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.control))
            .background(Palette.raised)
            .border(1.dp, Palette.hairline, RoundedCornerShape(Radius.control))
            .clickable(onClick = onDismiss)
            .padding(start = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message.text, style = Type.secondary, color = Palette.text, modifier = Modifier.weight(1f).padding(vertical = Space.m))
        message.actionLabel?.let { TextAction(it, onAction, color = Palette.text) }
        Spacer(Modifier.width(Space.s))
    }
}

/** Asked when leaving an editor with unsaved changes */
@Composable
fun DiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        containerColor = Palette.card,
        title = { Text("Änderungen verwerfen", style = Type.section, color = Palette.text) },
        text = { Text("Deine Änderungen sind noch nicht gespeichert.", style = Type.secondary, color = Palette.textSecondary) },
        confirmButton = { TextAction("Verwerfen", onDiscard, color = Palette.text) },
        dismissButton = { TextAction("Weiter bearbeiten", onKeep) },
    )
}

/**
 * Top bar of every screen one level down: back arrow, title, and optionally the
 * preview switch (light and sound on the device). While the preview runs, a line
 * under the bar says so.
 */
@Composable
fun EditorBar(
    title: String,
    onBack: (() -> Unit)?,
    preview: Boolean? = null,
    onPreview: ((Boolean) -> Unit)? = null,
) {
    Column {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconAction(Line.back, "Zurück", onBack) else Spacer(Modifier.width(Space.l))
            Text(title, style = Type.section, color = Palette.text, modifier = Modifier.weight(1f))
            if (preview != null && onPreview != null) {
                IconAction(Line.eye, if (preview) "Vorschau am Wecker beenden" else "Vorschau am Wecker", { onPreview(!preview) }, active = preview)
            }
        }
        if (preview == true) {
            Row(
                Modifier.fillMaxWidth().background(Palette.raised).padding(horizontal = Space.page, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.accent))
                Spacer(Modifier.width(Space.s))
                Text("Vorschau läuft am Wecker", style = Type.secondary, color = Palette.text, modifier = Modifier.weight(1f))
                TextAction("Beenden", { onPreview?.invoke(false) })
            }
        }
    }
}
