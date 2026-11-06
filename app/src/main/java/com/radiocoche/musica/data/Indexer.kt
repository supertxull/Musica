package com.radiocoche.musica.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/**
 * Prepara el índice de una fuente en segundo plano.
 *
 * - Recorre todas las carpetas y apunta las canciones.
 * - Solo lee las etiquetas de las canciones nuevas o cambiadas (por tamaño y fecha);
 *   las demás ya están en el índice, así que la segunda vez va muy rápido.
 * - Borra del índice lo que ya no está en el disco.
 */
class Indexer(private val db: LibraryDb) {

    suspend fun run(source: Source, progress: (done: Int, total: Int) -> Unit) {
        val root = source.root
        val rootKey = root.absolutePath

        // 1. Lista de canciones.
        val files = ArrayList<File>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val d = stack.removeLast()
            val (dirs, songs) = listFolder(d)
            files.addAll(songs)
            dirs.forEach { if (it.name != "Android") stack.addLast(it) }
        }

        // 2. Leer solo lo nuevo o cambiado.
        val known = db.known(rootKey)
        val present = HashSet<String>(files.size * 2)
        val batch = ArrayList<Track>()
        var done = 0
        progress(0, files.size)
        for (f in files) {
            currentCoroutineContext().ensureActive()
            val p = f.absolutePath
            present.add(p)
            val stamp = f.length() * 31 + f.lastModified()
            if (known[p] != stamp) {
                batch.add(Meta.read(f, rootKey))
                if (batch.size >= 50) {
                    db.upsert(batch)
                    batch.clear()
                }
            }
            done++
            if (done % 25 == 0) progress(done, files.size)
        }
        db.upsert(batch)

        // 3. Quitar lo que ya no existe.
        db.delete(known.keys.filter { it !in present })
        progress(files.size, files.size)
    }
}
