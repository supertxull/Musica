package com.radiocoche.musica.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

/** Lectura de etiquetas (título, artista, álbum) y carátulas de los archivos. */
object Meta {

    fun read(file: File, root: String): Track {
        var title = ""
        var artist = ""
        var album = ""
        var duration = 0L
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.absolutePath)
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().trim()
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().trim()
            if (artist.isEmpty()) artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).orEmpty().trim()
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().trim()
            duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
        } finally {
            runCatching { r.release() }
        }
        val folder = file.parentFile
        return Track(
            path = file.absolutePath,
            root = root,
            folder = folder?.absolutePath ?: "",
            size = file.length(),
            mtime = file.lastModified(),
            title = title.ifEmpty { file.nameWithoutExtension },
            artist = artist.ifEmpty { "Artista desconocido" },
            album = album.ifEmpty { folder?.name ?: "Álbum desconocido" },
            duration = duration,
        )
    }

    /** Carátula incluida en el archivo o, si no hay, cover.jpg / folder.jpg de su carpeta. */
    fun cover(file: File, maxPx: Int = 440): Bitmap? {
        val r = MediaMetadataRetriever()
        val bytes = try {
            r.setDataSource(file.absolutePath)
            r.embeddedPicture
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { r.release() }
        }
        if (bytes != null) {
            val embedded = decode(maxPx) { o -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o) }
            if (embedded != null) return trimMargins(embedded)
        }

        val dir = file.parentFile ?: return null
        val names = listOf("cover", "folder", "front", "albumart", "album")
        val img = runCatching { dir.listFiles() }.getOrNull()?.firstOrNull { f ->
            val n = f.nameWithoutExtension.lowercase()
            val e = f.extension.lowercase()
            (e == "jpg" || e == "jpeg" || e == "png") && names.any { n.startsWith(it) }
        } ?: return null
        return decode(maxPx) { o -> BitmapFactory.decodeFile(img.absolutePath, o) }?.let { trimMargins(it) }
    }

    /**
     * Quita los márgenes lisos blancos o negros que traen algunas carátulas
     * (marcos o bandas), para que la portada rellene todo el cuadro.
     */
    fun trimMargins(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        if (w < 32 || h < 32) return b
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)

        fun lum(c: Int) = ((c shr 16 and 0xFF) * 3 + (c shr 8 and 0xFF) * 6 + (c and 0xFF)) / 10
        // Liso: casi blanco o casi negro, con algo de margen para el ruido del JPG.
        fun plain(c: Int): Int = when (lum(c)) { in 232..255 -> 1; in 0..22 -> 2; else -> 0 }

        val kind = plain(px[0]).takeIf { it != 0 } ?: plain(px[w * h - 1]).takeIf { it != 0 } ?: return b
        fun ok(c: Int) = plain(c) == kind
        fun rowPlain(y: Int): Boolean { var n = 0; for (x in 0 until w) if (!ok(px[y * w + x])) n++; return n <= w / 50 }
        fun colPlain(x: Int, y0: Int, y1: Int): Boolean { var n = 0; for (y in y0 until y1) if (!ok(px[y * w + x])) n++; return n <= (y1 - y0) / 50 }

        var top = 0
        while (top < h / 2 && rowPlain(top)) top++
        var bottom = h
        while (bottom > top + 1 && rowPlain(bottom - 1)) bottom--
        var left = 0
        while (left < w / 2 && colPlain(left, top, bottom)) left++
        var right = w
        while (right > left + 1 && colPlain(right - 1, top, bottom)) right--

        val cw = right - left
        val ch = bottom - top
        // Nada que recortar, o la portada es casi toda blanca/negra: se deja como está.
        if ((cw == w && ch == h) || cw < w * 0.3 || ch < h * 0.3) return b
        return runCatching { Bitmap.createBitmap(b, left, top, cw, ch) }.getOrDefault(b)
    }

    private inline fun decode(maxPx: Int, load: (BitmapFactory.Options) -> Bitmap?): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        load(bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        load(BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (_: Throwable) {
        null
    }
}
