package de.hauptmann.sunshinealarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import de.hauptmann.sunshinealarm.api.AlarmSettings
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Radius
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.Type

/** Pieces shared by the editors (alarm, nap, sunset, radio preset) */

/** Small gradient chip showing a light theme's colors */
@Composable
fun ThemeSwatch(theme: Int, reversed: Boolean = false) {
    Canvas(Modifier.size(width = 32.dp, height = 20.dp).clip(RoundedCornerShape(6.dp))) {
        drawRect(Brush.horizontalGradient(themeColors(theme).let { if (reversed) it.reversed() else it }))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(hour, minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.card,
        shape = RoundedCornerShape(Radius.card + Space.xs),
        text = {
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Palette.raised,
                    clockDialSelectedContentColor = Palette.onAccent,
                    clockDialUnselectedContentColor = Palette.textSecondary,
                    selectorColor = Palette.accent,
                    containerColor = Palette.card,
                    timeSelectorSelectedContainerColor = Palette.raised,
                    timeSelectorUnselectedContainerColor = Palette.deepest,
                    timeSelectorSelectedContentColor = Palette.text,
                    timeSelectorUnselectedContentColor = Palette.textSecondary,
                ),
            )
        },
        confirmButton = { TextAction("Übernehmen", { onConfirm(state.hour, state.minute) }, color = Palette.text) },
        dismissButton = { TextAction("Abbrechen", onDismiss) },
    )
}

@Composable
fun NameField(
    value: String,
    placeholder: String = "z. B. Werktags",
    maxLength: Int = AlarmSettings.MAX_NAME_LENGTH,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(maxLength)) },
        placeholder = { Text(placeholder, style = Type.body, color = Palette.textMuted) },
        textStyle = Type.body.copy(color = Palette.text),
        singleLine = true,
        shape = RoundedCornerShape(Radius.control),
        supportingText = { Text("${value.length} / $maxLength", style = Type.meta, color = Palette.textMuted) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.textMuted,
            unfocusedBorderColor = Palette.hairline,
            cursorColor = Palette.accent,
            focusedContainerColor = Palette.deepest,
            unfocusedContainerColor = Palette.deepest,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

fun radioLabel(preset: Int, presets: List<RadioPreset>): String =
    presets.firstOrNull { it.number == preset }?.let { p -> listOfNotNull(p.label, p.frequency).joinToString(" · ") } ?: "Sender $preset"
