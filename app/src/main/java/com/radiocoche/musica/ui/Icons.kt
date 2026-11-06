package com.radiocoche.musica.ui

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Un trazo del icono: su forma, si va relleno, si lleva línea y su grosor. */
private class P(val d: String, val fill: Boolean = false, val stroke: Boolean = true, val w: Float = 2f)

private fun icon(name: String, vararg parts: P): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    for (p in parts) {
        b.addPath(
            pathData = PathParser().parsePathString(p.d).toNodes(),
            fill = if (p.fill) SolidColor(Color.White) else null,
            stroke = if (p.stroke) SolidColor(Color.White) else null,
            strokeLineWidth = p.w,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return b.build()
}

/** Iconos dibujados a mano, los mismos del diseño. */
object Ic {
    val Prev = icon("prev", P("M5 5v14", w = 2.2f), P("M19 5L9 12l10 7z", fill = true, w = 2.2f))
    val Next = icon("next", P("M5 5l10 7-10 7z", fill = true, w = 2.2f), P("M19 5v14", w = 2.2f))
    val Play = icon("play", P("M7 4l13 8-13 8z", fill = true, stroke = false))
    val Pause = icon("pause", P("M6 4h4v16h-4z", fill = true, stroke = false), P("M14 4h4v16h-4z", fill = true, stroke = false))
    val Heart = icon("heart", P("M12 20s-7-4.4-9-9a5 5 0 0 1 9-3 5 5 0 0 1 9 3c-2 4.6-9 9-9 9z"))
    val HeartOn = icon("heartOn", P("M12 20s-7-4.4-9-9a5 5 0 0 1 9-3 5 5 0 0 1 9 3c-2 4.6-9 9-9 9z", fill = true))
    val Shuffle = icon("shuffle", P("M16 3h5v5M4 20L21 3M21 16v5h-5M15 15l6 6M4 4l5 5"))
    val Repeat = icon(
        "repeat",
        P("M17 2l4 4-4 4"), P("M3 11V9a3 3 0 0 1 3-3h15"), P("M7 22l-4-4 4-4"), P("M21 13v2a3 3 0 0 1-3 3H3"),
    )
    val Library = icon(
        "list",
        P("M8 6h13M8 12h13M8 18h13", w = 2.2f),
        P("M2.3 6a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0", fill = true, stroke = false),
        P("M2.3 12a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0", fill = true, stroke = false),
        P("M2.3 18a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0", fill = true, stroke = false),
    )
    val Eq = icon("eq", P("M6 3v18M12 3v18M18 3v18"), P("M3.5 15h5M9.5 8h5M15.5 13h5", w = 3f))
    val Gear = icon(
        "gear",
        P("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", w = 1.8f),
        P(
            "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z",
            w = 1.8f,
        ),
    )
    val Bluetooth = icon("bt", P("M7 7l10 10-5 5V2l5 5L7 17"))
    val Person = icon("person", P("M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0"), P("M4 21a8 8 0 0 1 16 0"))
    val Disc = icon("disc", P("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0"), P("M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0"))
    val Folder = icon("folder", P("M3 6a1 1 0 0 1 1-1h5l2 2h9a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z"))
    val Back = icon("back", P("M15 5l-7 7 7 7", w = 2.4f))
}
