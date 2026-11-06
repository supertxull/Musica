package com.radiocoche.musica.data

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

enum class Kind { USB, SD, INTERNAL, BT }

/** Una fuente de música: un USB, la tarjeta SD, la memoria interna o el Bluetooth. */
data class Source(
    val kind: Kind,
    val root: File,
    val label: String,
) {
    /** Identificador estable mientras la fuente esté conectada en la misma ruta. */
    val id: String get() = if (kind == Kind.BT) "BT" else root.absolutePath
}

/**
 * Detecta qué hay conectado.
 *
 * Usa dos caminos y une los resultados:
 * 1. Los volúmenes extraíbles que anuncia Android (StorageManager).
 * 2. Las rutas que usa el reproductor de fábrica de la TS10
 *    (/storage/usb…, /mnt/usbhost…, /storage/extsd…).
 * Un mismo USB puede aparecer en las dos, así que se descartan duplicados
 * comparando la ruta real y el contenido de la carpeta raíz.
 */
object Sources {

    private val usbPaths = listOf(
        "/storage/usb", "/storage/usbotg", "/storage/udisk", "/storage/usbdisk",
        "/mnt/usbhost", "/mnt/usb_storage", "/mnt/udisk", "/mnt/usb",
    )
    private val sdPaths = listOf("/storage/extsd", "/mnt/extsd", "/storage/sdcard1", "/mnt/sdcard1", "/mnt/external_sd")

    fun detect(ctx: Context): List<Source> {
        val found = ArrayList<Pair<Kind, File>>()
        val seenReal = HashSet<String>()
        val seenSig = HashSet<String>()

        fun add(dir: File, kind: Kind) {
            val files = runCatching { dir.listFiles() }.getOrNull() ?: return
            if (files.isEmpty()) return
            val real = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
            val sig = files.map { it.name }.sorted().take(40).joinToString("|")
            if (!seenReal.add(real) || !seenSig.add(sig)) return
            found.add(kind to dir)
        }

        // 1. Volúmenes de Android.
        if (Build.VERSION.SDK_INT >= 24) {
            runCatching {
                val sm = ctx.getSystemService(StorageManager::class.java)
                for (v in sm.storageVolumes) {
                    if (!v.isRemovable) continue
                    val st = v.state
                    if (st != Environment.MEDIA_MOUNTED && st != Environment.MEDIA_MOUNTED_READ_ONLY) continue
                    val dir = volumeDir(v) ?: continue
                    val desc = (runCatching { v.getDescription(ctx) }.getOrNull() ?: "").lowercase()
                    add(dir, guessKind(dir, desc))
                }
            }
        }

        // 2. Rutas conocidas de la unidad.
        for (base in sdPaths) candidates(base).forEach { add(it, Kind.SD) }
        for (base in usbPaths) candidates(base).forEach { add(it, Kind.USB) }

        // 3. Cualquier otro disco montado en /storage (los USB suelen salir como /storage/XXXX-XXXX).
        runCatching {
            File("/storage").listFiles()?.sortedBy { it.name }?.forEach { d ->
                val n = d.name.lowercase()
                if (n == "emulated" || n == "self" || n == "sdcard0") return@forEach
                if (d.isDirectory) add(d, guessKind(d, ""))
            }
        }

        val usbs = found.filter { it.first == Kind.USB }.sortedBy { it.second.absolutePath }
        val sd = found.firstOrNull { it.first == Kind.SD }

        val out = ArrayList<Source>()
        usbs.forEachIndexed { i, (_, dir) ->
            out.add(Source(Kind.USB, dir, if (usbs.size > 1) "USB${i + 1}" else "USB"))
        }
        if (sd != null) out.add(Source(Kind.SD, sd.second, "Tarjeta SD"))
        val custom = ctx.getSharedPreferences("musica", Context.MODE_PRIVATE).getString("internal_folder", null)
        out.add(Source(Kind.INTERNAL, internalRoot(custom), "Memoria interna"))
        out.add(Source(Kind.BT, File("/"), "Bluetooth"))
        return out
    }

    /** Raíz de la memoria interna (/storage/emulated/0). */
    @Suppress("DEPRECATION")
    fun internalBase(): File = Environment.getExternalStorageDirectory()

    /**
     * Carpeta de música de la memoria interna: la elegida en Configuración o, por defecto,
     * la carpeta Music (o la raíz si no existe).
     */
    fun internalRoot(custom: String? = null): File {
        if (!custom.isNullOrEmpty()) {
            val f = File(custom)
            if (f.isDirectory) return f
        }
        val base = internalBase()
        val music = File(base, "Music")
        return if (music.isDirectory) music else base
    }

    private fun guessKind(dir: File, desc: String): Kind {
        val p = dir.absolutePath.lowercase()
        return if (desc.contains("sd") || p.contains("extsd") || p.contains("sdcard")) Kind.SD else Kind.USB
    }

    /** La ruta base y sus variantes numeradas, más subcarpetas tipo usbhost/Storage01. */
    private fun candidates(base: String): List<File> {
        val out = ArrayList<File>()
        val names = listOf(base) + (0..4).map { "$base$it" }
        for (n in names) {
            val d = File(n)
            if (!d.isDirectory) continue
            val kids = runCatching { d.listFiles() }.getOrNull() ?: continue
            // Si dentro solo hay carpetas tipo "Storage01" / "udisk1", el contenido real está ahí.
            val mounts = kids.filter { it.isDirectory && Regex("(?i)(storage|udisk|usb|disk|part)\\w*\\d+").matches(it.name) }
            if (mounts.isNotEmpty() && mounts.size == kids.size) out.addAll(mounts) else out.add(d)
        }
        return out
    }

    private fun volumeDir(v: StorageVolume): File? {
        if (Build.VERSION.SDK_INT >= 30) return v.directory
        return runCatching { v.javaClass.getMethod("getPathFile").invoke(v) as File }.getOrNull()
            ?: runCatching { File(v.javaClass.getMethod("getPath").invoke(v) as String) }.getOrNull()
    }
}

/** Extensiones de audio que se reproducen. */
private val audioExt = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "ape", "amr", "mka")

fun File.isAudio(): Boolean = isFile && extension.lowercase() in audioExt

/** Carpetas y canciones de una carpeta, en orden alfabético. */
fun listFolder(dir: File): Pair<List<File>, List<File>> {
    val all = runCatching { dir.listFiles() }.getOrNull() ?: return emptyList<File>() to emptyList()
    val dirs = all.filter { it.isDirectory && !it.isHidden && it.name != "LOST.DIR" && it.name != "System Volume Information" }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    val songs = all.filter { it.isAudio() }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    return dirs to songs
}

/** Primera carpeta (en orden) que contiene canciones. Para empezar a sonar al cambiar de fuente. */
fun firstMusicFolder(root: File, maxDirs: Int = 3000): File? {
    val stack = ArrayDeque<File>()
    stack.addLast(root)
    var visited = 0
    while (stack.isNotEmpty() && visited < maxDirs) {
        val d = stack.removeFirst()
        visited++
        val (dirs, songs) = listFolder(d)
        if (songs.isNotEmpty()) return d
        dirs.asReversed().forEach { stack.addFirst(it) }
    }
    return null
}
