package de.hauptmann.sunshinealarm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.hauptmann.sunshinealarm.R

/*
 * Design direction (unslop by Damir Sokolovsky, @heydamir, CC BY 4.0):
 *   Product    Control the wake-up light and see how the bedroom was at night, local and private
 *   Character  calm, a little warm, exact
 *   World      dawn in a bedroom, the passage from night to sunrise
 *   Moments    the sunrise curve of the next alarm, the night climate chart
 *   Signature  the horizon line with a small sun travelling towards the next alarm
 *
 * Colors avoid red vs green for meaning (red-green colorblind user): states are
 * always spelled out in text, the only chrome accent is amber.
 */
object Palette {
    val deepest = Color(0xFF0F0F10)
    val background = Color(0xFF141415)
    val card = Color(0xFF19191B)
    val raised = Color(0xFF232325)
    val text = Color(0xFFEDEDED)
    val textSecondary = Color(0xFFA1A1A6)
    val textMuted = Color(0xFF85858B)
    val hairline = text.copy(alpha = 0.08f)
    val accent = Color(0xFFF0A23B)       // dawn amber, 8.9:1 on background
    val onAccent = Color(0xFF141415)
    /** Fill of the primary button: off-white, a step below the text so it does not glare in the dark */
    val primaryFill = Color(0xFFD6D6D9)
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val section = 40.dp
    val page = 20.dp
    val row = 52.dp
}

object Radius {
    val control = 10.dp
    val card = 16.dp
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Figtree = FontFamily(variable(R.font.figtree, 400), variable(R.font.figtree, 500), variable(R.font.figtree, 600))
val Mono = FontFamily(variable(R.font.jetbrains_mono, 400), variable(R.font.jetbrains_mono, 500))

object Type {
    /** The next alarm time, the largest thing on the home screen */
    val hero = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 64.sp, lineHeight = 68.sp, letterSpacing = (-0.03).em)
    val title = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.W500, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em)
    val figure = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em)
    val section = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.W500, fontSize = 16.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 22.sp)
    val secondary = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp)
    val value = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 15.sp, lineHeight = 20.sp)
    val meta = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp)
    val button = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.W500, fontSize = 15.sp, lineHeight = 20.sp)
}

@Composable
fun SunshineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Palette.background,
            surface = Palette.background,
            surfaceContainer = Palette.card,
            surfaceContainerHigh = Palette.raised,
            surfaceContainerHighest = Palette.raised,
            surfaceVariant = Palette.raised,
            onBackground = Palette.text,
            onSurface = Palette.text,
            onSurfaceVariant = Palette.textSecondary,
            primary = Palette.accent,
            onPrimary = Palette.onAccent,
            secondary = Palette.text,
            outline = Palette.hairline,
            outlineVariant = Palette.hairline,
            error = Palette.text,
        ),
        content = content,
    )
}
