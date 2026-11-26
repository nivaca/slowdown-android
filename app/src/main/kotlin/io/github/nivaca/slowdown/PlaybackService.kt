// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * El servicio de reproducción: deja sonar con la pantalla apagada o con otra
 * aplicación delante. Es un servicio en primer plano («mediaPlayback») con una
 * sesión de medios y su notificación: atrás, tocar/pausa y adelante en la
 * pantalla de bloqueo y en la notificación, y los botones de los auriculares.
 * Mientras suena, Android no congela la aplicación; en pausa, la notificación
 * se queda (para seguir desde allí) pero ya se puede quitar.
 *
 * También pausa si se desconectan los auriculares. Sigue a la ventana mirando
 * el motor cada medio segundo: si se pausa o termina, lo muestra.
 */
package io.github.nivaca.slowdown

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class PlaybackService : Service() {

    private lateinit var media: MediaSession
    private val handler = Handler(Looper.getMainLooper())
    private var shownPlaying: Boolean? = null
    private var shownTitle: String? = null
    private var foreground = false
    private var noisyRegistered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.playback_channel),
            NotificationManager.IMPORTANCE_LOW))
        media = MediaSession(this, "Slowdown").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = Playback.play(this@PlaybackService)
                override fun onPause() = Playback.pause(this@PlaybackService)
                override fun onStop() = close()
                override fun onRewind() = Playback.jump(this@PlaybackService, -skip())
                override fun onFastForward() = Playback.jump(this@PlaybackService, skip())
                override fun onSkipToPrevious() = Playback.jump(this@PlaybackService, -skip())
                override fun onSkipToNext() = Playback.jump(this@PlaybackService, skip())
                override fun onSeekTo(pos: Long) {
                    Playback.seek(this@PlaybackService, pos / 1000.0)
                    refresh()
                }
            })
            setSessionActivity(openApp())
            isActive = true
        }
        handler.post(watch)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Empezado con startForegroundService, debe pasar por el primer plano enseguida, aunque luego se detenga
        if (!foreground) {
            try {
                startForeground(ID, notification(Engine.playing(), Playback.source?.name ?: getString(R.string.app_name)),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                foreground = true
            } catch (_: Exception) {
                // (Android no lo deja desde el fondo: sigue como servicio común)
            }
        }
        when (intent?.action) {
            ACTION_TOGGLE -> Playback.toggle(this)
            ACTION_BACK -> Playback.jump(this, -skip())
            ACTION_FORWARD -> Playback.jump(this, skip())
            ACTION_CLOSE -> {
                close()
                return START_NOT_STICKY
            }
        }
        refresh(force = true)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(watch)
        if (noisyRegistered) unregisterReceiver(noisy)
        media.release()
        super.onDestroy()
    }

    private fun skip() = Playback.prefs(this).getInt("skip", 5).toDouble()

    // Se mira el motor cada medio segundo: lo que cambió en la ventana, el final del archivo
    private val watch = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 500)
        }
    }

    private fun close() {
        // Pausa, guarda y quita la notificación; el archivo sigue abierto en la ventana
        if (Engine.playing()) Engine.pause()
        Playback.save(this)
        Playback.abandonFocus(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun refresh(force: Boolean = false) {
        if (!Playback.loaded) {
            if (foreground || force) close()
            return
        }
        Playback.takeEnded()  // (al final del archivo, vuelve adonde empezó a tocar, también sin la ventana)
        val playing = Engine.playing()
        val title = Playback.source?.name ?: getString(R.string.app_name)
        // El estado va siempre (la posición avanza), la notificación solo cuando cambia algo
        media.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_REWIND or PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP)
            .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                (Engine.position() * 1000).toLong(), if (playing) Playback.session.speed.toFloat() else 0f)
            .build())
        if (!force && playing == shownPlaying && title == shownTitle) return
        shownPlaying = playing
        shownTitle = title
        media.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.app_name))
            .putLong(MediaMetadata.METADATA_KEY_DURATION, (Engine.duration() * 1000).toLong())
            .build())
        val notification = notification(playing, title)
        if (playing) {
            try {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                foreground = true
            } catch (_: Exception) {
                getSystemService(NotificationManager::class.java).notify(ID, notification)
            }
            if (!noisyRegistered) {
                registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                noisyRegistered = true
            }
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
            foreground = false
            getSystemService(NotificationManager::class.java).notify(ID, notification)
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE)

    private fun command(action: String, code: Int): PendingIntent = PendingIntent.getForegroundService(this, code,
        Intent(this, PlaybackService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)

    private fun notification(playing: Boolean, title: String): Notification {
        val skip = skip().toInt()
        fun action(icon: Int, text: String, act: String, code: Int) =
            Notification.Action.Builder(Icon.createWithResource(this, icon), text, command(act, code)).build()
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(getString(R.string.speed_value, "%.2f".format(java.util.Locale.ROOT, Playback.session.speed)))
            .setContentIntent(openApp())
            .setDeleteIntent(command(ACTION_CLOSE, 4))
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(R.drawable.ic_back, getString(R.string.back, skip), ACTION_BACK, 1))
            .addAction(if (playing) action(R.drawable.ic_pause, getString(R.string.pause), ACTION_TOGGLE, 2)
                else action(R.drawable.ic_play, getString(R.string.play), ACTION_TOGGLE, 2))
            .addAction(action(R.drawable.ic_forward, getString(R.string.forward, skip), ACTION_FORWARD, 3))
            .setStyle(Notification.MediaStyle().setMediaSession(media.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    // Se desconectaron los auriculares: no sigue sonando por el altavoz
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Engine.playing()) Playback.pause(context)
        }
    }

    companion object {
        private const val CHANNEL = "playback"
        private const val ID = 1
        const val ACTION_TOGGLE = "io.github.nivaca.slowdown.TOGGLE"
        const val ACTION_BACK = "io.github.nivaca.slowdown.BACK"
        const val ACTION_FORWARD = "io.github.nivaca.slowdown.FORWARD"
        const val ACTION_CLOSE = "io.github.nivaca.slowdown.CLOSE"

        @Volatile var running = false

        /** Tells the service that something changed; STARTING: playing begins, so it must come to the front.
         * Without STARTING, it does nothing if the service is not there (nothing has played). */
        fun update(context: Context, starting: Boolean = false) {
            if (!starting && !running) return
            val intent = Intent(context, PlaybackService::class.java)
            try {
                if (starting) context.startForegroundService(intent) else context.startService(intent)
            } catch (_: IllegalStateException) {
                // (desde el fondo Android no deja empezarlo; el servicio, si existe, lo verá en medio segundo)
            }
        }
    }
}
