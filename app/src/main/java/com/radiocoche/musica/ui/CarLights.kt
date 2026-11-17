package com.radiocoche.musica.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Luces del coche. El microcontrolador de la unidad (TopWay TS10) avisa con el mensaje 0x204:
 * arg1 = 1 al encenderlas y 0 al apagarlas (lo mismo que usa la Radio).
 * Se guarda el último estado para arrancar ya con los colores correctos.
 * Fuera de la unidad (sin `android.tw.john.TWUtil`) no hace nada.
 */
object CarLights {
    private const val MSG_LIGHTS = 0x204

    /** true = encendidas; false = apagadas; null = aún no se sabe. */
    var on by mutableStateOf<Boolean?>(null)
        private set

    private var started = false
    private var tw: Any? = null // se guarda para que no se libere
    private var onChange: (() -> Unit)? = null

    fun start(context: Context, onChange: () -> Unit) {
        this.onChange = onChange
        if (started) return
        started = true
        val prefs = context.applicationContext.getSharedPreferences("luces", Context.MODE_PRIVATE)
        if (prefs.contains("on")) on = prefs.getBoolean("on", false)
        try {
            val cls = Class.forName("android.tw.john.TWUtil")
            val util = cls.getConstructor(Int::class.javaPrimitiveType).newInstance(1)
            val res = cls.getMethod("open", ShortArray::class.java).invoke(util, shortArrayOf(MSG_LIGHTS.toShort())) as? Int ?: 0
            if (res != 0) return
            cls.getMethod("start").invoke(util)
            val handler = Handler(Looper.getMainLooper()) { m ->
                if (m.what == MSG_LIGHTS) {
                    val v = m.arg1 != 0
                    on = v
                    prefs.edit().putBoolean("on", v).apply()
                    this.onChange?.invoke()
                }
                true
            }
            cls.getMethod("addHandler", String::class.java, Handler::class.java).invoke(util, "Luces", handler)
            // Pide el estado actual (la Radio hace lo mismo).
            runCatching {
                cls.getMethod("write", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(util, MSG_LIGHTS, 255)
            }
            tw = util
        } catch (_: Throwable) {
        }
    }
}
