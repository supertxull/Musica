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
            if (embedded != null) return embedded
        }

        val dir = file.parentFile ?: return null
        val names = listOf("cover", "folder", "front", "albumart", "album")
        val img = runCatching { dir.listFiles() }.getOrNull()?.firstOrNull { f ->
            val n = f.nameWithoutExtension.lowercase()
            val e = f.extension.lowercase()
            (e == "jpg" || e == "jpeg" || e == "png") && names.any { n.startsWith(it) }
        } ?: return null
        return decode(maxPx) { o -> BitmapFactory.decodeFile(img.absolutePath, o) }
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
