package com.radiocoche.musica.data

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/** Una línea de la letra. [timeMs] es null si la letra no está sincronizada. */
data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>) {
    val synced: Boolean get() = lines.isNotEmpty() && lines.all { it.timeMs != null }
}

/**
 * Lee la letra guardada dentro del propio archivo de música.
 *
 * - MP3: etiquetas ID3v2 USLT (letra) y SYLT (letra sincronizada).
 * - FLAC: comentarios LYRICS / UNSYNCEDLYRICS.
 * - M4A / AAC: átomo ©lyr.
 *
 * Si el texto de la letra trae marcas de tiempo tipo LRC ([01:23.45]), se trata como sincronizada:
 * así lo guardan Mp3tag, MusicBrainz Picard y otros programas.
 */
object LyricsReader {

    fun read(file: File): Lyrics? = runCatching {
        when (file.extension.lowercase()) {
            "mp3" -> readId3(file)
            "flac" -> readFlac(file)
            "m4a", "mp4", "aac", "alac" -> readMp4(file)
            else -> readId3(file)
        }
    }.getOrNull()?.takeIf { it.lines.any { l -> l.text.isNotBlank() } }

    // ---------- Texto LRC ----------

    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val metaTag = Regex("""^\[(ar|ti|al|au|by|re|ve|length|offset|la|id):.*]$""", RegexOption.IGNORE_CASE)

    /** Convierte un texto de letra (LRC o normal) en líneas. */
    fun parseText(raw: String): Lyrics {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        val offset = Regex("""\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val timed = ArrayList<LyricLine>()
        var plainCount = 0
        for (line in text.split('\n')) {
            val l = line.trim()
            if (metaTag.matches(l)) continue
            val stamps = stamp.findAll(l).toList()
            if (stamps.isNotEmpty() && l.startsWith("[")) {
                val words = l.substring(stamps.last().range.last + 1).replace(Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>"""), "").trim()
                for (m in stamps) {
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3]
                    val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                    timed.add(LyricLine((min * 60_000 + sec * 1000 + ms - offset).coerceAtLeast(0), words.ifEmpty { "♪" }))
                }
            } else if (l.isNotEmpty()) {
                plainCount++
            }
        }
        // Si casi todo lleva tiempos, es una letra sincronizada.
        if (timed.isNotEmpty() && timed.size >= plainCount) return Lyrics(timed.sortedBy { it.timeMs })
        return Lyrics(text.split('\n').map { LyricLine(null, it.trim()) }.dropWhile { it.text.isEmpty() })
    }

    // ---------- MP3 (ID3v2) ----------

    private fun readId3(file: File): Lyrics? {
        RandomAccessFile(file, "r").use { f ->
            val head = ByteArray(10)
            if (f.read(head) != 10) return null
            if (head[0] != 'I'.code.toByte() || head[1] != 'D'.code.toByte() || head[2] != '3'.code.toByte()) return null
            val ver = head[3].toInt()
            if (ver < 2 || ver > 4) return null
            val flags = head[5].toInt()
            val size = syncsafe(head, 6)
            if (size <= 0 || size > 64 * 1024 * 1024) return null
            var tag = ByteArray(size)
            f.readFully(tag)
            // "Unsynchronisation" de la etiqueta completa (v2.2 / v2.3).
            if (flags and 0x80 != 0 && ver < 4) tag = unsync(tag)
            var pos = 0
            // Cabecera extendida.
            if (flags and 0x40 != 0 && ver >= 3) {
                val ext = if (ver == 4) syncsafe(tag, 0) else be32(tag, 0) + 4
                pos = ext.coerceIn(0, tag.size)
            }
            var sylt: Lyrics? = null
            var uslt: Lyrics? = null
            val idLen = if (ver == 2) 3 else 4
            val hdrLen = if (ver == 2) 6 else 10
            while (pos + hdrLen <= tag.size) {
                val id = String(tag, pos, idLen, Charsets.ISO_8859_1)
                if (id[0] == '\u0000') break
                val fsize = when (ver) {
                    2 -> (tag[pos + 3].toInt() and 0xFF shl 16) or (tag[pos + 4].toInt() and 0xFF shl 8) or (tag[pos + 5].toInt() and 0xFF)
                    3 -> be32(tag, pos + 4)
                    else -> syncsafe(tag, pos + 4)
                }
                val fflags = if (ver == 2) 0 else (tag[pos + 9].toInt() and 0xFF)
                val start = pos + hdrLen
                if (fsize <= 0 || start + fsize > tag.size) break
                var data = tag.copyOfRange(start, start + fsize)
                if (ver == 4 && fflags and 0x02 != 0) data = unsync(data)
                if (ver == 4 && fflags and 0x01 != 0 && data.size > 4) data = data.copyOfRange(4, data.size) // longitud de datos
                val compressedOrEncrypted = (ver == 3 && fflags and 0xC0 != 0) || (ver == 4 && fflags and 0x0C != 0)
                if (!compressedOrEncrypted) {
                    when (id) {
                        "USLT", "ULT" -> if (uslt == null) uslt = parseUslt(data)
                        "SYLT", "SLT" -> if (sylt == null) sylt = parseSylt(data)
                    }
                }
                pos = start + fsize
            }
            // Preferimos la sincronizada.
            return sylt?.takeIf { it.lines.isNotEmpty() } ?: uslt
        }
    }

    private fun parseUslt(d: ByteArray): Lyrics? {
        if (d.size < 5) return null
        val enc = d[0].toInt()
        var p = 4 // codificación + idioma
        p = skipString(d, p, enc) // descripción
        if (p > d.size) return null
        val text = decode(d, p, d.size, enc)
        return parseText(text)
    }

    private fun parseSylt(d: ByteArray): Lyrics? {
        if (d.size < 7) return null
        val enc = d[0].toInt()
        val format = d[4].toInt() // 1 = fotogramas MPEG, 2 = milisegundos
        if (format != 2) return null
        var p = skipString(d, 6, enc)
        val out = ArrayList<LyricLine>()
        while (p < d.size) {
            val end = findTerm(d, p, enc)
            val text = decode(d, p, end, enc)
            p = end + termLen(enc)
            if (p + 4 > d.size) break
            val t = be32(d, p).toLong() and 0xFFFFFFFFL
            p += 4
            out.add(LyricLine(t, text.trim()))
        }
        if (out.isEmpty()) return null
        return Lyrics(out.filter { it.text.isNotEmpty() }.sortedBy { it.timeMs })
    }

    private fun termLen(enc: Int) = if (enc == 1 || enc == 2) 2 else 1

    private fun findTerm(d: ByteArray, from: Int, enc: Int): Int {
        if (enc == 1 || enc == 2) {
            var i = from
            while (i + 1 < d.size) {
                if (d[i].toInt() == 0 && d[i + 1].toInt() == 0) return i
                i += 2
            }
            return d.size
        }
        var i = from
        while (i < d.size && d[i].toInt() != 0) i++
        return i
    }

    private fun skipString(d: ByteArray, from: Int, enc: Int): Int = findTerm(d, from, enc) + termLen(enc)

    private fun decode(d: ByteArray, from: Int, to: Int, enc: Int): String {
        if (from >= to) return ""
        val cs: Charset = when (enc) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        return String(d, from, to - from, cs).trimEnd('\u0000')
    }

    private fun syncsafe(b: ByteArray, o: Int) =
        (b[o].toInt() and 0x7F shl 21) or (b[o + 1].toInt() and 0x7F shl 14) or (b[o + 2].toInt() and 0x7F shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun be32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF shl 24) or (b[o + 1].toInt() and 0xFF shl 16) or (b[o + 2].toInt() and 0xFF shl 8) or (b[o + 3].toInt() and 0xFF)

    /** Deshace la "unsynchronisation" de ID3 (FF 00 → FF). */
    private fun unsync(b: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if (b[i].toInt() and 0xFF == 0xFF && i + 1 < b.size && b[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    // ---------- FLAC ----------

    private fun readFlac(file: File): Lyrics? {
        RandomAccessFile(file, "r").use { f ->
            val magic = ByteArray(4)
            f.readFully(magic)
            // Algunos FLAC llevan además una etiqueta ID3 delante.
            if (String(magic, 0, 3, Charsets.ISO_8859_1) == "ID3") return readId3(file)
            if (String(magic, Charsets.ISO_8859_1) != "fLaC") return null
            while (true) {
                val h = ByteArray(4)
                if (f.read(h) != 4) return null
                val last = h[0].toInt() and 0x80 != 0
                val type = h[0].toInt() and 0x7F
                val len = (h[1].toInt() and 0xFF shl 16) or (h[2].toInt() and 0xFF shl 8) or (h[3].toInt() and 0xFF)
                if (type == 4) {
                    val b = ByteArray(len)
                    f.readFully(b)
                    return parseVorbisComments(b)
                }
                f.seek(f.filePointer + len)
                if (last) return null
            }
        }
    }

    private fun le32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or (b[o + 1].toInt() and 0xFF shl 8) or (b[o + 2].toInt() and 0xFF shl 16) or (b[o + 3].toInt() and 0xFF shl 24)

    private fun parseVorbisComments(b: ByteArray): Lyrics? {
        var p = 0
        val vendor = le32(b, p); p += 4 + vendor
        if (p + 4 > b.size) return null
        val n = le32(b, p); p += 4
        var unsynced: String? = null
        var lyrics: String? = null
        repeat(n) {
            if (p + 4 > b.size) return@repeat
            val l = le32(b, p); p += 4
            if (l < 0 || p + l > b.size) return@repeat
            val s = String(b, p, l, Charsets.UTF_8); p += l
            val eq = s.indexOf('=')
            if (eq > 0) {
                when (s.substring(0, eq).uppercase()) {
                    "LYRICS", "SYNCEDLYRICS" -> if (lyrics == null) lyrics = s.substring(eq + 1)
                    "UNSYNCEDLYRICS", "UNSYNCED LYRICS" -> if (unsynced == null) unsynced = s.substring(eq + 1)
                }
            }
        }
        return (lyrics ?: unsynced)?.let { parseText(it) }
    }

    // ---------- M4A (MP4) ----------

    private val containers = setOf("moov", "udta", "ilst", "trak", "mdia", "minf", "stbl")

    private fun readMp4(file: File): Lyrics? {
        RandomAccessFile(file, "r").use { f -> return findLyr(f, 0, f.length(), 0) }
    }

    private fun findLyr(f: RandomAccessFile, start: Long, end: Long, depth: Int): Lyrics? {
        if (depth > 8) return null
        var pos = start
        val h = ByteArray(8)
        while (pos + 8 <= end) {
            f.seek(pos)
            f.readFully(h)
            var size = (be32(h, 0).toLong() and 0xFFFFFFFFL)
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            var hdr = 8L
            if (size == 1L) {
                val ext = ByteArray(8); f.readFully(ext)
                size = (be32(ext, 0).toLong() shl 32) or (be32(ext, 4).toLong() and 0xFFFFFFFFL)
                hdr = 16
            } else if (size == 0L) {
                size = end - pos
            }
            if (size < hdr) return null
            when {
                type == "©lyr" -> {
                    // Dentro: átomo "data" con 8 bytes de tipo/idioma y luego el texto UTF-8.
                    val len = (size - hdr).toInt().coerceAtMost(2 * 1024 * 1024)
                    val b = ByteArray(len)
                    f.seek(pos + hdr)
                    f.readFully(b)
                    if (len > 16 && String(b, 4, 4, Charsets.ISO_8859_1) == "data") {
                        val dl = be32(b, 0).coerceAtMost(len)
                        return parseText(String(b, 16, dl - 16, Charsets.UTF_8))
                    }
                    return null
                }
                type == "meta" -> findLyr(f, pos + hdr + 4, pos + size, depth + 1)?.let { return it }
                type in containers -> findLyr(f, pos + hdr, pos + size, depth + 1)?.let { return it }
            }
            pos += size
        }
        return null
    }
}
