package com.radiocoche.musica

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.radiocoche.musica.data.Indexer
import com.radiocoche.musica.data.Kind
import com.radiocoche.musica.data.LibraryDb
import com.radiocoche.musica.data.Meta
import com.radiocoche.musica.data.Source
import com.radiocoche.musica.data.Sources
import com.radiocoche.musica.data.Track
import com.radiocoche.musica.data.firstMusicFolder
import com.radiocoche.musica.data.listFolder
import com.radiocoche.musica.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { PLAYER, LIBRARY }
enum class LibView(val label: String) { FOLDERS("Carpetas"), ARTISTS("Artistas"), ALBUMS("Álbumes"), SONGS("Canciones"), FAVS("Favoritas") }
enum class RowKind { FOLDER, ARTIST, ALBUM, SONG }

data class LibRow(
    val kind: RowKind,
    val title: String,
    val sub: String = "",
    val end: String = "",
    val path: String = "",
    val songIndex: Int = -1,
    val fav: Boolean = false,
)

data class NowPlaying(
    val path: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val folder: String = "",
    val index: Int = 0,
    val count: Int = 0,
)

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context get() = getApplication<Application>()
    private val prefs = app.getSharedPreferences("musica", Context.MODE_PRIVATE)
    private val db = LibraryDb(app)
    private val indexer = Indexer(db)

    // ---------- Estado de pantalla ----------
    var screen by mutableStateOf(Screen.PLAYER)
    var hasPermission by mutableStateOf(false)
    var sources by mutableStateOf<List<Source>>(emptyList())
        private set
    var current by mutableStateOf<Source?>(null)
        private set

    var now by mutableStateOf(NowPlaying())
        private set
    var cover by mutableStateOf<ImageBitmap?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_ALL)
        private set
    var favorite by mutableStateOf(false)
        private set
    var toast by mutableStateOf("")
        private set

    // ---------- Biblioteca ----------
    var libSource by mutableStateOf<Source?>(null)
        private set
    var libView by mutableStateOf(LibView.FOLDERS)
        private set
    var rows by mutableStateOf<List<LibRow>>(emptyList())
        private set
    var heading by mutableStateOf("")
        private set
    var canGoUp by mutableStateOf(false)
        private set
    var emptyMsg by mutableStateOf("")
        private set
    var indexText by mutableStateOf("")
        private set

    private var folderStack: List<File> = emptyList()
    private var selection: String? = null
    private var listing: List<String> = emptyList()
    private var lastUsbId: String? = null

    private var controller: MediaController? = null
    private var queue: List<String> = emptyList()
    private var restored = false
    private var toastJob: Job? = null
    private var reloadJob: Job? = null
    private var indexJob: Job? = null
    private val indexedThisSession = HashSet<String>()

    init {
        connect()
        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(500)
                controller?.let { c ->
                    positionMs = c.currentPosition.coerceAtLeast(0)
                    val d = c.duration
                    if (d != C.TIME_UNSET && d > 0) durationMs = d
                }
                tick++
                if (tick % 10 == 0) savePosition()
                if (tick % 10 == 5 && hasPermission) refreshSources()
            }
        }
    }

    // ---------- Conexión con el servicio de reproducción ----------

    private fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            shuffle = c.shuffleModeEnabled
            repeat = c.repeatMode
            if (c.mediaItemCount > 0) {
                // El servicio ya estaba sonando (por ejemplo, al volver a abrir la app).
                restored = true
                queue = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
                current = sources.firstOrNull { queue.firstOrNull()?.startsWith(it.root.absolutePath + "/") == true } ?: current
                playing = c.isPlaying
                updateNow()
            } else {
                c.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_ALL)
                c.shuffleModeEnabled = prefs.getBoolean("shuffle", false)
                tryRestore()
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playing = isPlaying
            savePosition()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateNow()
            savePosition()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffle = shuffleModeEnabled
            prefs.edit().putBoolean("shuffle", shuffleModeEnabled).apply()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            repeat = repeatMode
            prefs.edit().putInt("repeat", repeatMode).apply()
        }

        override fun onPlayerError(error: PlaybackException) {
            val c = controller ?: return
            val path = c.currentMediaItem?.mediaId ?: ""
            if (path.isNotEmpty() && !File(path).exists()) {
                showToast("La fuente se ha desconectado")
                c.stop()
                refreshSources()
            } else {
                showToast("No se puede reproducir este archivo")
                if (c.hasNextMediaItem()) {
                    c.seekToNextMediaItem()
                    c.prepare()
                    c.play()
                }
            }
        }
    }

    private fun updateNow() {
        val c = controller ?: return
        val item = c.currentMediaItem ?: return
        val path = item.mediaId
        val index = c.currentMediaItemIndex
        val count = c.mediaItemCount
        val file = File(path)
        now = NowPlaying(
            path = path,
            title = file.nameWithoutExtension,
            folder = file.parentFile?.name ?: "",
            index = index,
            count = count,
        )
        cover = null
        viewModelScope.launch {
            val t: Track = withContext(Dispatchers.IO) { db.get(path) ?: Meta.read(file, current?.root?.absolutePath ?: "") }
            val fav = withContext(Dispatchers.IO) { db.isFav(path) }
            if (now.path != path) return@launch
            now = now.copy(title = t.title, artist = t.artist, album = t.album)
            favorite = fav
            if (t.duration > 0 && (c.duration == C.TIME_UNSET || c.duration <= 0)) durationMs = t.duration
            val bmp = withContext(Dispatchers.IO) { Meta.cover(file) }
            if (now.path == path) cover = bmp?.asImageBitmap()
        }
    }

    // ---------- Permisos y fuentes ----------

    fun onPermission(granted: Boolean) {
        hasPermission = granted
        if (granted) {
            refreshSources()
            tryRestore()
        } else {
            showToast("Sin permiso para leer la música")
        }
    }

    fun refreshSources() {
        if (!hasPermission) return
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { Sources.detect(ctx) }
            if (found.map { it.id + it.label } == sources.map { it.id + it.label }) return@launch
            sources = found
            // Si la fuente que sonaba ya no está, se para.
            current?.let { cur -> if (found.none { it.id == cur.id }) current = found.firstOrNull { it.kind == Kind.USB } ?: found.firstOrNull { it.kind == Kind.INTERNAL } }
            libSource?.let { ls -> if (found.none { it.id == ls.id }) { libSource = current; folderStack = emptyList(); selection = null; reload() } }
            current = current?.let { cur -> found.firstOrNull { it.id == cur.id } } ?: current
            if (current == null && queue.isNotEmpty()) current = found.firstOrNull { queue.first().startsWith(it.root.absolutePath + "/") }
            libSource = libSource?.let { ls -> found.firstOrNull { it.id == ls.id } } ?: libSource
            startIndexing()
        }
    }

    private fun startIndexing() {
        if (indexJob?.isActive == true) return
        val pending = sources.filter { it.kind != Kind.BT && it.id !in indexedThisSession }
        if (pending.isEmpty()) return
        indexJob = viewModelScope.launch(Dispatchers.IO) {
            for (s in pending) {
                indexedThisSession.add(s.id)
                runCatching {
                    indexer.run(s) { done, total ->
                        val txt = if (done >= total) "" else "Preparando biblioteca de ${s.label}: $done de $total"
                        viewModelScope.launch(Dispatchers.Main) { indexText = txt }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                indexText = ""
                if (libView != LibView.FOLDERS) reload()
            }
        }
    }

    // ---------- Recordar dónde se quedó ----------

    private fun queueFile(src: Source) = File(ctx.filesDir, "cola_" + Integer.toHexString(src.id.hashCode()) + ".txt")

    private fun saveQueue() {
        val src = current ?: return
        val list = queue
        viewModelScope.launch(Dispatchers.IO) { runCatching { queueFile(src).writeText(list.joinToString("\n")) } }
    }

    private fun savePosition() {
        val c = controller ?: return
        val src = current ?: return
        if (queue.isEmpty()) return
        prefs.edit()
            .putString("last_source", src.id)
            .putInt("idx_" + src.id, c.currentMediaItemIndex)
            .putLong("pos_" + src.id, c.currentPosition.coerceAtLeast(0))
            .putBoolean("was_playing", c.playWhenReady)
            .apply()
    }

    /** Al abrir la app sigue en la misma canción y el mismo segundo. */
    private fun tryRestore() {
        if (restored || controller == null || !hasPermission) return
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { Sources.detect(ctx) }
            sources = found
            restored = true
            val lastId = prefs.getString("last_source", null)
            val src = found.firstOrNull { it.id == lastId && it.kind != Kind.BT }
                ?: found.firstOrNull { it.kind == Kind.USB }
                ?: found.firstOrNull { it.kind == Kind.INTERNAL }
            if (src != null) startSource(src, autoplay = lastId == null || prefs.getBoolean("was_playing", true))
            startIndexing()
        }
    }

    /** Empieza a sonar una fuente: donde se dejó o, si es nueva, en su primera carpeta con música. */
    private fun startSource(src: Source, autoplay: Boolean) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { queueFile(src).readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())
            }
            if (saved.isNotEmpty() && File(saved.first()).exists()) {
                val idx = prefs.getInt("idx_" + src.id, 0).coerceIn(0, saved.size - 1)
                val pos = prefs.getLong("pos_" + src.id, 0L)
                play(saved, idx, src, pos, autoplay)
                return@launch
            }
            val folder = withContext(Dispatchers.IO) { firstMusicFolder(src.root) }
            if (folder == null) {
                current = src
                showToast("No hay música en ${src.label}")
                return@launch
            }
            val songs = withContext(Dispatchers.IO) { listFolder(folder).second.map { it.absolutePath } }
            play(songs, 0, src, 0L, autoplay)
        }
    }

    private fun play(paths: List<String>, start: Int, src: Source, posMs: Long = 0L, autoplay: Boolean = true) {
        val c = controller ?: return
        if (paths.isEmpty()) return
        val items = paths.map { p ->
            val f = File(p)
            MediaItem.Builder()
                .setMediaId(p)
                .setUri(Uri.fromFile(f))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(f.nameWithoutExtension).setAlbumTitle(f.parentFile?.name).build())
                .build()
        }
        current = src
        queue = paths
        c.setMediaItems(items, start.coerceIn(0, items.size - 1), posMs)
        c.prepare()
        if (autoplay) c.play() else c.pause()
        saveQueue()
        savePosition()
    }

    // ---------- Controles del reproductor ----------

    fun togglePlay() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            current?.let { startSource(it, true) } ?: showToast("No hay música para reproducir")
            return
        }
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun previous() = controller?.seekToPrevious() ?: Unit
    fun next() = controller?.seekToNext() ?: Unit

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val d = if (c.duration > 0) c.duration else Long.MAX_VALUE
        c.seekTo((c.currentPosition + deltaMs).coerceIn(0, d))
        positionMs = c.currentPosition
    }

    fun seekToFraction(f: Float) {
        val c = controller ?: return
        val d = if (c.duration > 0) c.duration else durationMs
        if (d <= 0) return
        val target = (d * f.coerceIn(0f, 1f)).toLong()
        c.seekTo(target)
        positionMs = target
    }

    fun toggleShuffle() {
        val c = controller ?: return
        val v = !c.shuffleModeEnabled
        c.shuffleModeEnabled = v
        showToast(if (v) "Aleatorio activado" else "Aleatorio desactivado")
    }

    fun cycleRepeat() {
        val c = controller ?: return
        val r = when (c.repeatMode) {
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_ALL
        }
        c.repeatMode = r
        showToast(
            when (r) {
                Player.REPEAT_MODE_ALL -> "Repetir todas"
                Player.REPEAT_MODE_ONE -> "Repetir esta canción"
                else -> "Repetición desactivada"
            }
        )
    }

    fun toggleFavorite() {
        val path = now.path
        if (path.isEmpty()) return
        val v = !favorite
        favorite = v
        val root = current?.root?.absolutePath ?: ""
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (v && db.get(path) == null) db.upsert(listOf(Meta.read(File(path), root)))
                db.setFav(path, v)
            }
            if (libView == LibView.FAVS) reload()
        }
        showToast(if (v) "Añadida a favoritas" else "Quitada de favoritas")
    }

    /** Botón Fuente: pasa a la siguiente fuente conectada. */
    fun cycleSource() {
        val list = sources.filter { it.kind != Kind.BT }
        if (list.size < 2) {
            showToast("No hay otras fuentes conectadas")
            return
        }
        val i = list.indexOfFirst { it.id == current?.id }
        val nxt = list[(i + 1) % list.size]
        savePosition()
        showToast("Fuente: ${nxt.label}")
        startSource(nxt, true)
    }

    fun openBluetooth() {
        if (!openApp("com.tw.bt", "com.tw.btmusic", "com.android.bluetooth")) showToast("No encuentro la app de Bluetooth")
    }

    fun openEq() {
        if (!openApp("com.tw.eq")) showToast("No encuentro el ecualizador")
    }

    private fun openApp(vararg pkgs: String): Boolean {
        for (p in pkgs) {
            val i = ctx.packageManager.getLaunchIntentForPackage(p) ?: continue
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { ctx.startActivity(i) }.isSuccess) return true
        }
        return false
    }

    fun showToast(msg: String) {
        toastJob?.cancel()
        toast = msg
        toastJob = viewModelScope.launch {
            delay(2200)
            toast = ""
        }
    }

    // ---------- Biblioteca ----------

    fun openLibrary() {
        val src = libSource?.takeIf { s -> sources.any { it.id == s.id } }
            ?: current?.takeIf { it.kind != Kind.BT }
            ?: sources.firstOrNull { it.kind != Kind.BT }
        if (src?.id != libSource?.id) {
            libSource = src
            folderStack = emptyList()
            selection = null
        }
        screen = Screen.LIBRARY
        reload()
    }

    /** Botón de fuente de la Biblioteca. El de USB va pasando por los USB conectados. */
    fun pickLibrarySource(kind: Kind) {
        if (kind == Kind.BT) {
            openBluetooth()
            return
        }
        val ofKind = sources.filter { it.kind == kind }
        if (ofKind.isEmpty()) return
        val cur = libSource
        val target = if (kind == Kind.USB && cur?.kind == Kind.USB && ofKind.size > 1) {
            val i = ofKind.indexOfFirst { it.id == cur.id }
            ofKind[(i + 1) % ofKind.size].also { showToast("Fuente: ${it.label}") }
        } else if (kind == Kind.USB) {
            ofKind.firstOrNull { it.id == lastUsbId } ?: ofKind.first()
        } else {
            ofKind.first()
        }
        if (target.kind == Kind.USB) lastUsbId = target.id
        libSource = target
        folderStack = emptyList()
        selection = null
        reload()
    }

    /** Etiqueta del botón USB de la Biblioteca. */
    fun usbLabel(): String {
        val usbs = sources.filter { it.kind == Kind.USB }
        val shown = libSource?.takeIf { it.kind == Kind.USB } ?: usbs.firstOrNull { it.id == lastUsbId } ?: usbs.firstOrNull()
        return shown?.label ?: "USB"
    }

    fun setView(v: LibView) {
        libView = v
        folderStack = emptyList()
        selection = null
        reload()
    }

    fun goUp() {
        if (selection != null) selection = null
        else if (folderStack.isNotEmpty()) folderStack = folderStack.dropLast(1)
        reload()
    }

    fun tapRow(row: LibRow) {
        when (row.kind) {
            RowKind.FOLDER -> { folderStack = folderStack + File(row.path); reload() }
            RowKind.ARTIST, RowKind.ALBUM -> { selection = row.title; reload() }
            RowKind.SONG -> {
                val src = libSource ?: return
                play(listing, row.songIndex, src)
                showToast("Reproduciendo: ${row.title}")
            }
        }
    }

    fun playAll() {
        val src = libSource ?: return
        if (listing.isNotEmpty()) play(listing, 0, src)
    }

    fun reload() {
        val src = libSource
        reloadJob?.cancel()
        if (src == null) {
            rows = emptyList(); heading = ""; canGoUp = false; listing = emptyList()
            emptyMsg = "No hay ninguna fuente con música conectada."
            return
        }
        val view = libView
        val stack = folderStack
        val sel = selection
        val nowPath = now.path
        reloadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { build(src, view, stack, sel, nowPath) }
            rows = result.rows
            listing = result.songs
            heading = result.heading
            emptyMsg = result.empty
            canGoUp = sel != null || (view == LibView.FOLDERS && stack.isNotEmpty())
        }
    }

    private class Built(val rows: List<LibRow>, val songs: List<String>, val heading: String, val empty: String)

    private fun fmt(ms: Long): String {
        if (ms <= 0) return ""
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }

    private fun songRows(list: List<Track>): Pair<List<LibRow>, List<String>> {
        val rows = list.mapIndexed { i, t ->
            LibRow(RowKind.SONG, t.title, "${t.artist} · ${t.album}", fmt(t.duration), t.path, i)
        }
        return rows to list.map { it.path }
    }

    private fun build(src: Source, view: LibView, stack: List<File>, sel: String?, nowPath: String): Built {
        val root = src.root.absolutePath
        val preparing = if (db.count(root) == 0) "Preparando la biblioteca… Mientras tanto puedes usar Carpetas." else ""
        return when (view) {
            LibView.FOLDERS -> {
                val dir = stack.lastOrNull() ?: src.root
                val (dirs, songs) = listFolder(dir)
                val meta = db.inFolder(dir.absolutePath)
                val r = ArrayList<LibRow>(dirs.size + songs.size)
                dirs.forEach { r.add(LibRow(RowKind.FOLDER, it.name, path = it.absolutePath)) }
                songs.forEachIndexed { i, f ->
                    val t = meta[f.absolutePath]
                    r.add(
                        LibRow(
                            RowKind.SONG,
                            t?.title ?: f.nameWithoutExtension,
                            if (t != null) "${t.artist} · ${t.album}" else "",
                            if (t != null) fmt(t.duration) else "",
                            f.absolutePath,
                            i,
                        )
                    )
                }
                Built(r, songs.map { it.absolutePath }, if (stack.isEmpty()) "/" + src.label else dir.name, "Esta carpeta está vacía.")
            }
            LibView.ARTISTS -> if (sel == null) {
                Built(db.artists(root).map { LibRow(RowKind.ARTIST, it.name, it.sub, plural(it.count)) }, emptyList(), "Artistas", preparing.ifEmpty { "No hay artistas." })
            } else {
                val (r, s) = songRows(db.songsOfArtist(root, sel))
                Built(r, s, sel, "No hay canciones.")
            }
            LibView.ALBUMS -> if (sel == null) {
                Built(db.albums(root).map { LibRow(RowKind.ALBUM, it.name, it.sub, plural(it.count)) }, emptyList(), "Álbumes", preparing.ifEmpty { "No hay álbumes." })
            } else {
                val (r, s) = songRows(db.songsOfAlbum(root, sel))
                Built(r, s, sel, "No hay canciones.")
            }
            LibView.SONGS -> {
                val (r, s) = songRows(db.allSongs(root))
                Built(r, s, "Todas las canciones", preparing.ifEmpty { "No hay canciones." })
            }
            LibView.FAVS -> {
                val (r, s) = songRows(db.favSongs(root))
                Built(r, s, "Favoritas", "Pulsa el corazón en el reproductor para añadir canciones aquí.")
            }
        }
    }

    private fun plural(n: Int) = if (n == 1) "1 canción" else "$n canciones"

    override fun onCleared() {
        savePosition()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        super.onCleared()
    }
}
