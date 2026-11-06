package com.radiocoche.musica.playback

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

/**
 * Servicio que reproduce la música, también con la app en segundo plano.
 *
 * Publica una sesión multimedia estándar de Android: así funcionan los mandos del volante
 * y, más adelante, el launcher podrá mostrar la canción y controlarla.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // Las canciones llegan solo con su ruta (mediaId); aquí se convierte en algo reproducible.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> {
                    val out = mediaItems.map { item ->
                        val uri = item.localConfiguration?.uri
                            ?: item.requestMetadata.mediaUri
                            ?: Uri.fromFile(File(item.mediaId))
                        item.buildUpon().setUri(uri).build()
                    }.toMutableList()
                    return Futures.immediateFuture(out)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // Si se cierra la app desde "recientes", la música sigue sonando, como en la radio de fábrica.
    override fun onTaskRemoved(rootIntent: Intent?) {}

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
