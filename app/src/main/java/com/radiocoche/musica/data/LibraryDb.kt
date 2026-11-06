package com.radiocoche.musica.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Datos de una canción guardados en el índice. */
data class Track(
    val path: String,
    val root: String,
    val folder: String,
    val size: Long,
    val mtime: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
)

data class Group(val name: String, val sub: String, val count: Int)

/**
 * Índice propio de la biblioteca: una pequeña base de datos en la unidad.
 * Solo hace falta para Artistas, Álbumes, Canciones y Favoritas;
 * la vista Carpetas lee el disco directamente.
 */
class LibraryDb(ctx: Context) : SQLiteOpenHelper(ctx, "biblioteca.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tracks(path TEXT PRIMARY KEY, root TEXT, folder TEXT, size INTEGER, mtime INTEGER, " +
                "title TEXT, artist TEXT, album TEXT, duration INTEGER)"
        )
        db.execSQL("CREATE INDEX tracks_root ON tracks(root)")
        db.execSQL("CREATE INDEX tracks_folder ON tracks(folder)")
        db.execSQL("CREATE TABLE favs(path TEXT PRIMARY KEY)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun readTracks(sql: String, args: Array<String>): List<Track> {
        val out = ArrayList<Track>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                out.add(
                    Track(
                        c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4),
                        c.getString(5) ?: "", c.getString(6) ?: "", c.getString(7) ?: "", c.getLong(8),
                    )
                )
            }
        }
        return out
    }

    private val cols = "path, root, folder, size, mtime, title, artist, album, duration"

    fun get(path: String): Track? = readTracks("SELECT $cols FROM tracks WHERE path = ?", arrayOf(path)).firstOrNull()

    fun inFolder(folder: String): Map<String, Track> =
        readTracks("SELECT $cols FROM tracks WHERE folder = ?", arrayOf(folder)).associateBy { it.path }

    /** Ruta → (tamaño, fecha) de lo ya indexado en una fuente, para leer solo lo nuevo. */
    fun known(root: String): HashMap<String, Long> {
        val out = HashMap<String, Long>()
        readableDatabase.rawQuery("SELECT path, size, mtime FROM tracks WHERE root = ?", arrayOf(root)).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getLong(1) * 31 + c.getLong(2)
        }
        return out
    }

    fun upsert(list: List<Track>) {
        if (list.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (t in list) {
                val v = ContentValues().apply {
                    put("path", t.path); put("root", t.root); put("folder", t.folder)
                    put("size", t.size); put("mtime", t.mtime)
                    put("title", t.title); put("artist", t.artist); put("album", t.album)
                    put("duration", t.duration)
                }
                db.insertWithOnConflict("tracks", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun delete(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (p in paths) db.delete("tracks", "path = ?", arrayOf(p))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun count(root: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM tracks WHERE root = ?", arrayOf(root)).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun artists(root: String): List<Group> {
        val out = ArrayList<Group>()
        readableDatabase.rawQuery(
            "SELECT artist, COUNT(DISTINCT album), COUNT(*) FROM tracks WHERE root = ? GROUP BY artist ORDER BY artist COLLATE NOCASE",
            arrayOf(root),
        ).use { c ->
            while (c.moveToNext()) {
                val albums = c.getInt(1)
                out.add(Group(c.getString(0) ?: "", if (albums == 1) "1 álbum" else "$albums álbumes", c.getInt(2)))
            }
        }
        return out
    }

    fun albums(root: String): List<Group> {
        val out = ArrayList<Group>()
        readableDatabase.rawQuery(
            "SELECT album, GROUP_CONCAT(DISTINCT artist), COUNT(*) FROM tracks WHERE root = ? GROUP BY album ORDER BY album COLLATE NOCASE",
            arrayOf(root),
        ).use { c ->
            while (c.moveToNext()) out.add(Group(c.getString(0) ?: "", (c.getString(1) ?: "").replace(",", ", "), c.getInt(2)))
        }
        return out
    }

    fun songsOfArtist(root: String, artist: String) =
        readTracks("SELECT $cols FROM tracks WHERE root = ? AND artist = ? ORDER BY album COLLATE NOCASE, path", arrayOf(root, artist))

    fun songsOfAlbum(root: String, album: String) =
        readTracks("SELECT $cols FROM tracks WHERE root = ? AND album = ? ORDER BY folder, path", arrayOf(root, album))

    fun allSongs(root: String) =
        readTracks("SELECT $cols FROM tracks WHERE root = ? ORDER BY title COLLATE NOCASE", arrayOf(root))

    fun favSongs(root: String) = readTracks(
        "SELECT t.path, t.root, t.folder, t.size, t.mtime, t.title, t.artist, t.album, t.duration " +
            "FROM favs f JOIN tracks t ON t.path = f.path WHERE t.root = ? ORDER BY t.title COLLATE NOCASE",
        arrayOf(root),
    )

    fun isFav(path: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM favs WHERE path = ?", arrayOf(path)).use { it.moveToFirst() }

    /** Para "Actualizar": se vacía el índice y se vuelve a leer todo. Las favoritas se conservan. */
    fun clearTracks() {
        writableDatabase.delete("tracks", null, null)
    }

    fun clearFavs() {
        writableDatabase.delete("favs", null, null)
    }

    fun setFav(path: String, on: Boolean) {
        if (on) {
            writableDatabase.insertWithOnConflict("favs", null, ContentValues().apply { put("path", path) }, SQLiteDatabase.CONFLICT_IGNORE)
        } else {
            writableDatabase.delete("favs", "path = ?", arrayOf(path))
        }
    }
}
