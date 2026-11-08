package de.hauptmann.sunshinealarm.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** A few line icons from lucide (ISC license), 24 grid, 1.5 stroke */
object Line {
    private fun icon(name: String, vararg paths: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    val sunrise = icon(
        "sunrise", "M12 2v8", "m4.93 10.93 1.41 1.41", "M2 18h2", "M20 18h2", "m19.07 10.93-1.41 1.41",
        "M22 22H2", "m8 6 4-4 4 4", "M16 18a4 4 0 0 0-8 0",
    )
    val alarm = icon(
        "alarm", "M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16z", "M12 9v4l2 2", "M5 3 2 6", "m22 6-3-3",
    )
    val chart = icon("chart", "M3 3v16a2 2 0 0 0 2 2h16", "m19 9-5 5-4-4-3 3")
    val back = icon("back", "m12 19-7-7 7-7", "M19 12H5")
    val plus = icon("plus", "M5 12h14", "M12 5v14")
    val chevron = icon("chevron", "m9 18 6-6-6-6")
    val wifi = icon(
        "wifi", "M12 20h.01", "M2 8.82a15 15 0 0 1 20 0", "M5 12.86a10 10 0 0 1 14 0", "M8.5 16.43a5 5 0 0 1 7 0",
    )
    val trash = icon("trash", "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2", "M10 11v6", "M14 11v6")
    val sliders = icon(
        "sliders", "M21 4h-7", "M10 4H3", "M21 12h-9", "M8 12H3", "M21 20h-5", "M12 20H3", "M14 2v4", "M8 10v4", "M16 18v4",
    )
    val moon = icon("moon", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9z")
    val check = icon("check", "M20 6 9 17l-5-5")
    val play = icon("play", "M6 3l14 9-14 9V3z")
    val eye = icon("eye", "M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z", "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z")
    val alert = icon("alert", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M12 8v4", "M12 16h.01")
}
