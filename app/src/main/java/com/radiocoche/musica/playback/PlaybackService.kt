package com.radiocoche.musica.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
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
    private val handler = Handler(Looper.getMainLooper())

    /** Momento en que la música sonaba por última vez (para saber si sonaba al apagar el coche). */
    private var lastPlayingAt = 0L
    private var resumeOnWake = false

    /**
     * Reanudar al arrancar cuando la unidad despierta del reposo (sin reiniciar la app).
     * Al apagar, la unidad pausa la música justo antes de apagar la pantalla; si sonaba
     * en los segundos previos, al volver a encender la pantalla se reanuda.
     * Si estabas escuchando la radio, la música no estaba sonando y no se reanuda.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val player = session?.player ?: return
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    resumeOnWake = player.isPlaying || SystemClock.elapsedRealtime() - lastPlayingAt < 15_000
                }
                Intent.ACTION_SCREEN_ON -> {
                    val enabled = getSharedPreferences("musica", Context.MODE_PRIVATE).getBoolean("resume", true)
                    if (enabled && resumeOnWake && player.mediaItemCount > 0) {
                        handler.postDelayed({
                            if (!player.isPlaying) {
                                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                                player.play()
                            }
                        }, 1500)
                    }
                    resumeOnWake = false
                }
            }
        }
    }

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
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                lastPlayingAt = SystemClock.elapsedRealtime()
            }
        })
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

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
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
