package com.radiocoche.musica.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Luces del coche. El microcontrolador de la unidad (TopWay TS10) avisa con el mensaje 0x204:
 * arg1 = 1 al encenderlas y 0 al apagarlas (lo mismo que usa la Radio).
 * Al arrancar no se sabe (null): contraste alto hasta que la unidad diga que están encendidas.
 *
 * Dos vías, vale la primera que llegue:
 *  1. Conexión propia con el microcontrolador (se escucha el mismo grupo de mensajes que la Radio).
 *  2. El aviso [ACTION] que mandan las otras apps (Radio, Música) cuando ellas reciben el 0x204.
 * Cuando el aviso llega por la vía 1 se reenvía a las otras apps.
 *
 * Fuera de la unidad (sin `android.tw.john.TWUtil`) no hace nada.
 */
object CarLights {
    private const val MSG_LIGHTS = 0x204
    const val ACTION = "com.radiocoche.LIGHTS"
    private const val MSG_VOLUME = 0x203   // volumen general: arg1 = nivel (0–30), arg2 = máximo
    private const val MSG_NOW = 0x9F00      // lo que suena: arg1 = fuente (8 = Bluetooth), arg2 = 1 sonando / 0 en pausa, obj = título
    /** Fuente de sonido activa de la unidad: arg1 = 1 radio · 3 música de Android · 8 música Bluetooth… */
    private const val MSG_SOURCE_NOW = 0x9E08
    private val LISTEN = intArrayOf(0x201, MSG_VOLUME, MSG_LIGHTS, 0x301, MSG_NOW, MSG_SOURCE_NOW)
    private val OTHERS = listOf("com.radiocoche.radio", "com.radiocoche.launcher", "com.radiocoche.telefono")

    /** true = encendidas; false = apagadas; null = aún no se sabe. */
    var on by mutableStateOf<Boolean?>(null)
        private set

    /** Volumen general de la unidad (0–30), el del mando. null = aún no se sabe. */
    var volume: Int? = null
        private set

    /** La música Bluetooth está sonando (según la unidad). null = aún no se sabe. */
    var btPlaying by mutableStateOf<Boolean?>(null)
        private set

    /** Se llama cada vez que la unidad cambia de fuente de sonido. */
    var onSource: ((Int) -> Unit)? = null

    /** Fuente de sonido activa según la unidad (1 radio, 3 música, 8 Bluetooth). null = aún no se sabe. */
    var source by mutableStateOf<Int?>(null)
        private set

    /** Título de la canción Bluetooth según la unidad (mensaje 0x9F00). */
    var btTitle by mutableStateOf("")
        private set

    /** Estado de la conexión, para la línea de estado de la ventana de radares. */
    var link by mutableStateOf("sin conexión")
        private set

    private var started = false
    private var tw: Any? = null // se guarda para que no se libere
    private var write2: java.lang.reflect.Method? = null
    private var write3: java.lang.reflect.Method? = null
    private val listeners = mutableListOf<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var tries = 0

    /** Arranca la escucha (una sola vez) y añade [onChange] a los avisados. */
    fun start(context: Context, onChange: () -> Unit = {}) {
        if (!listeners.contains(onChange)) listeners += onChange
        if (started) return
        started = true
        app = context.applicationContext
        listenOthers(context.applicationContext)
        open()
    }

    private fun set(v: Boolean, fromMcu: Boolean) {
        val changed = on != v
        on = v
        if (fromMcu) {
            app?.let { ctx ->
                for (p in OTHERS) runCatching { ctx.sendBroadcast(Intent(ACTION).setPackage(p).putExtra("on", v)) }
            }
        }
        if (changed) listeners.toList().forEach { runCatching { it() } }
    }

    private fun open() {
        try {
            val cls = Class.forName("android.tw.john.TWUtil")
            val util = cls.getConstructor(Int::class.javaPrimitiveType).newInstance(1)
            val ids = ShortArray(LISTEN.size) { LISTEN[it].toShort() }
            val res = cls.getMethod("open", ShortArray::class.java).invoke(util, ids) as? Int ?: 0
            if (res != 0) {
                link = "error al conectar ($res)"
                retry()
                return
            }
            cls.getMethod("start").invoke(util)
            val handler = Handler(Looper.getMainLooper()) { m ->
                when (m.what) {
                    MSG_LIGHTS -> set(m.arg1 != 0, fromMcu = true)
                    MSG_VOLUME -> if (m.arg2 in 1..100 && m.arg1 in 0..m.arg2) volume = m.arg1
                    MSG_SOURCE_NOW -> {
                        source = m.arg1 and 0x7F
                        onSource?.invoke(m.arg1 and 0x7F)
                    }
                    MSG_NOW -> {
                        val was = btPlaying == true
                        btPlaying = m.arg1 == 8 && m.arg2 == 1
                        // Play en el móvil: la unidad no cambia sola de fuente; se le pide el Bluetooth (fuente 8).
                        if (btPlaying == true && !was && source != 8) {
                            runCatching { write3?.invoke(tw, 0x9E11, 192, 8) }
                        }
                        if (m.arg1 == 8) {
                            val o = m.obj
                            val t = when (o) {
                                is String -> o
                                is ByteArray -> String(o, Charsets.UTF_8)
                                else -> null
                            }
                            if (t != null) btTitle = t.trim { it <= ' ' || it == '\u0000' }
                        }
                    }
                }
                true
            }
            cls.getMethod("addHandler", String::class.java, Handler::class.java).invoke(util, "MusicaCoche", handler)
            val int = Int::class.javaPrimitiveType
            write2 = cls.getMethod("write", int, int)
            write3 = cls.getMethod("write", int, int, int)
            tw = util
            link = "conectado"
            ask()
            // El microcontrolador no siempre contesta a la primera: se vuelve a preguntar.
            main.postDelayed({ if (on == null) ask() }, 2000)
            main.postDelayed({ if (on == null) ask() }, 6000)
        } catch (_: ClassNotFoundException) {
            link = "fuera de la unidad"
        } catch (_: Throwable) {
            link = "error al conectar"
            retry()
        }
    }

    private fun retry() {
        if (++tries > 6) return
        main.postDelayed({ if (tw == null) open() }, 10_000)
    }

    private fun ask() {
        val util = tw ?: return
        runCatching { write2?.invoke(util, 0x203, 255) }
        runCatching { write2?.invoke(util, MSG_LIGHTS, 255) }
    }

    private fun listenOthers(ctx: Context) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.hasExtra("on")) set(i.getBooleanExtra("on", false), fromMcu = false)
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, IntentFilter(ACTION), Context.RECEIVER_EXPORTED)
            else ctx.registerReceiver(r, IntentFilter(ACTION))
        }
    }

    /**
     * Orden directa al microcontrolador con la conexión que ya está abierta (sin abrir ni cerrar nada).
     * La usa el aviso de radares para quitar y devolver el sonido de la radio al instante.
     * Devuelve false si no hay conexión (fuera de la unidad).
     */
    fun write(what: Int, a1: Int): Boolean {
        val util = tw ?: return false
        val m = write2 ?: return false
        return runCatching { m.invoke(util, what, a1) }.isSuccess
    }

    fun write(what: Int, a1: Int, a2: Int): Boolean {
        val util = tw ?: return false
        val m = write3 ?: return false
        return runCatching { m.invoke(util, what, a1, a2) }.isSuccess
    }
}
