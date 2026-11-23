package com.radiocoche.musica.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.radiocoche.musica.ui.CarLights

/**
 * Música por Bluetooth. La reproduce la app de fábrica `com.tw.bt` (aunque esté cerrada);
 * aquí solo se muestran sus datos y se le mandan órdenes.
 *
 *  - Datos de la canción: se piden con [GET_ID3] y llegan con [SEND_ID3]
 *    (musicTitle, musicArtist, musicProgress, musicDuration). También llegan solos al cambiar de canción.
 *  - Nombre del móvil: se pide con [GET_PAIR] y llega con [SEND_PAIR] (pairName).
 *  - Si suena o no: mensaje 0x9F00 del microcontrolador (ver [CarLights.btPlaying]).
 *  - Órdenes: aviso [BTMSG] con extra int "msg": 1 play/pausa, 3 siguiente, 4 anterior.
 */
class BtLink(private val ctx: Context) {

    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var phone by mutableStateOf("")
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var positionMs by mutableStateOf(0L)
        private set

    /** Sonando ahora mismo (según la unidad). */
    val playing: Boolean get() = CarLights.btPlaying == true

    /** Se llama cuando la música Bluetooth empieza a sonar. */
    var onStartedPlaying: (() -> Unit)? = null

    private var registered = false
    private var wasPlaying = false
    private var lastMcuTitle = ""
    private var posAt = 0L // momento en que llegó positionMs (para hacerla avanzar)
    private var posBase = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, i: Intent) {
            when (i.action) {
                SEND_ID3 -> {
                    title = i.getStringExtra("musicTitle")?.trim() ?: ""
                    artist = i.getStringExtra("musicArtist")?.trim() ?: ""
                    durationMs = toMs(i.getIntExtra("musicDuration", 0))
                    posBase = toMs(i.getIntExtra("musicProgress", 0))
                    posAt = SystemClock.elapsedRealtime()
                    positionMs = posBase
                }
                SEND_PAIR -> phone = i.getStringExtra("pairName")?.trim() ?: ""
            }
        }
    }

    /** La app de fábrica no dice la unidad: duraciones de más de 10 h en segundos no existen, así que es en ms. */
    private fun toMs(v: Int): Long = if (v in 1..36_000) v * 1000L else v.toLong().coerceAtLeast(0)

    fun start() {
        if (registered) return
        val f = IntentFilter().apply { addAction(SEND_ID3); addAction(SEND_PAIR) }
        ContextCompat.registerReceiver(ctx, receiver, f, ContextCompat.RECEIVER_EXPORTED)
        registered = true
        query()
    }

    fun query() {
        send(Intent(GET_ID3))
        send(Intent(GET_PAIR))
    }

    /** Dos veces por segundo: avanza la barra y avisa si empezó a sonar. */
    fun tick() {
        val now = playing
        if (now && !wasPlaying) {
            onStartedPlaying?.invoke()
            query()
        }
        wasPlaying = now
        if (now && posAt != 0L) {
            val p = posBase + (SystemClock.elapsedRealtime() - posAt)
            positionMs = if (durationMs > 0) p.coerceAtMost(durationMs) else p
        } else if (posAt != 0L) {
            posBase = positionMs
            posAt = SystemClock.elapsedRealtime()
        }
        // El título del microcontrolador llega antes que los datos completos.
        val t = CarLights.btTitle
        if (t != lastMcuTitle) {
            lastMcuTitle = t
            if (t.isNotEmpty() && t != title) {
                title = t
                artist = ""
                query()
            }
        }
    }

    /**
     * Pasa el sonido de la unidad al Bluetooth (como hace la app de fábrica antes de cada orden)
     * y, medio segundo después, pide que suene si estaba en pausa.
     */
    fun takeOver(handler: android.os.Handler) {
        CarLights.write(0x9E11, 192, 8)
        handler.postDelayed({ command(5) }, 600)
        query()
    }

    /** Pausa la música del móvil si está sonando (al pasar a otra fuente). */
    fun pauseIfPlaying() {
        if (playing) command(6)
    }

    fun playPause() = command(1)
    fun next() = command(3)
    fun previous() = command(4)

    private fun command(msg: Int) {
        send(Intent(BTMSG).putExtra("msg", msg))
    }

    private fun send(i: Intent) {
        runCatching { ctx.sendBroadcast(i.setPackage("com.tw.bt")) }
    }

    companion object {
        const val BTMSG = "com.tw.launcher.btmsg"
        const val GET_ID3 = "com.unisound.intent.action.GET_ID3_INFO"
        const val SEND_ID3 = "com.unisound.intent.action.SEND_ID3_INFO"
        const val GET_PAIR = "com.unisound.intent.action.GET_PAIR_INFO"
        const val SEND_PAIR = "com.unisound.intent.action.SEND_PAIR_INFO"
    }
}
