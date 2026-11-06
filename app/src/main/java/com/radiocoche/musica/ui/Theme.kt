package com.radiocoche.musica.ui

import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

/** Colores de la app. Los mismos que la Radio y el launcher. */
data class Palette(
    val bg: Color,
    val panel: Color,
    val raised: Color,
    val border: Color,
    val border2: Color,
    val text: Color,
    val muted: Color,
    val dim: Color,
    val accent: Color,
    val onAccent: Color,
    val accentBg: Color,
    val track: Color,
    val glow: Color,
)

/** Luces encendidas: los colores oscuros de antes. */
val NightPalette = Palette(
    bg = Color(0xFF0B0E13), panel = Color(0xFF151A22), raised = Color(0xFF1B212B),
    border = Color(0xFF232A35), border2 = Color(0xFF2A3240),
    text = Color(0xFFF2F4F7), muted = Color(0xFFA3ADBB), dim = Color(0xFF7A8494),
    accent = Color(0xFFFF8A1E), onAccent = Color(0xFF1A1205), accentBg = Color(0xFF2B1A0C),
    track = Color(0xFF343C4A), glow = Color(0x40FF8A1E),
)

/** Luces apagadas: contraste alto, para el sol. */
val ContrastPalette = Palette(
    bg = Color(0xFF000000), panel = Color(0xFF1C2230), raised = Color(0xFF2A3242),
    border = Color(0xFF56627A), border2 = Color(0xFF6E7B94),
    text = Color(0xFFFFFFFF), muted = Color(0xFFDCE2EA), dim = Color(0xFFA9B3C2),
    accent = Color(0xFFFF8A1E), onAccent = Color(0xFF140A00), accentBg = Color(0xFF4A2A08),
    track = Color(0xFF6E7B94), glow = Color(0x59FF8A1E),
)

object Tema {
    var p by mutableStateOf(ContrastPalette)

    /** Letra estrecha para títulos grandes (equivalente a Barlow Condensed del diseño). */
    val condensed: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
}

/**
 * Día / noche según las luces del coche.
 *
 * Las unidades TopWay guardan el modo en la propiedad del sistema
 * `persist.sys.tw.current.theme.type` ("day" o "night") y avisan de cada cambio con
 * el aviso [ACTION]. Es lo mismo que usan la Radio y el ecualizador de fábrica.
 */
object DayNight {
    const val ACTION = "com.tw.theme.day.night.change"
    private const val PROPERTY = "persist.sys.tw.current.theme.type"

    fun isNight(): Boolean = readProperty(PROPERTY) == "night"

    fun apply() {
        Tema.p = if (isNight()) NightPalette else ContrastPalette
    }

    private fun readProperty(name: String): String {
        val viaApi = runCatching {
            val cls = Class.forName("android.os.SystemProperties")
            cls.getMethod("get", String::class.java, String::class.java).invoke(null, name, "") as String
        }.getOrNull()
        if (!viaApi.isNullOrEmpty()) return viaApi
        return runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("getprop", name))
            p.inputStream.bufferedReader().use { it.readText() }.trim()
        }.getOrDefault("")
    }
}
