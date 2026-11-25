// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * Lo que se toca, fuera de la ventana: el archivo abierto, su .slx y el foco de
 * audio. La ventana puede cerrarse mientras suena (la pantalla apagada, otra
 * aplicación delante): el servicio de reproducción (PlaybackService.kt) sigue,
 * y al volver la ventana se une a lo que suena en lugar de abrirlo de nuevo.
 */
package io.github.nivaca.slowdown

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import java.io.File

object Playback {
    var uri: Uri? = null
    var source: Source? = null
    var session = Session()
    var loaded = false

    fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // La carpeta de los .slx, dentro de la aplicación; y, si se eligió, otra (la de la música, por ejemplo), que
    // se lee primero y se escribe también
    fun chosenFolder(context: Context): TreeFolder? {
        val tree = prefs(context).getString("slx_tree", null)?.let(Uri::parse) ?: return null
        val resolver = context.contentResolver
        val allowed = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        return if (allowed) TreeFolder(resolver, tree) else null
    }

    fun stores(context: Context): List<SlxFolder> =
        listOfNotNull(chosenFolder(context), FileFolder(File(context.filesDir, "slx")))

    private fun version(context: Context) =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    /** Writes the .slx of what is open, in each folder; the name it got (with its folder, if chosen) and the
     * errors, if any. */
    fun save(context: Context): Pair<String?, List<String>> {
        val source = source ?: return null to emptyList()
        if (!loaded) return null to emptyList()
        session.position = Engine.position()
        var shown: String? = null
        val errors = mutableListOf<String>()
        for (folder in stores(context)) {
            try {
                val name = Sidecar.save(session, source, folder, version(context))
                if (shown == null) shown = if (folder is TreeFolder) "${folder.label()}/$name" else name
            } catch (e: Exception) {
                errors += e.message ?: e.toString()
            }
        }
        return shown to errors
    }

    // --- tocar y pausar, desde la ventana, la notificación, los auriculares o el teclado

    fun play(context: Context) {
        if (!loaded) return
        requestFocus(context)
        Engine.play(0, 0.0, 0.0)
        PlaybackService.update(context, starting = true)
    }

    fun pause(context: Context) {
        Engine.pause()
        save(context)
        PlaybackService.update(context)
    }

    fun toggle(context: Context) = if (Engine.playing()) pause(context) else play(context)

    fun jump(context: Context, seconds: Double) {
        if (!loaded) return
        Engine.seek((Engine.position() + seconds).coerceIn(0.0, Engine.duration()))
        PlaybackService.update(context)
    }

    // --- el sonido de los demás: el foco de audio

    private var focusRequest: AudioFocusRequest? = null

    private fun requestFocus(context: Context) {
        val app = context.applicationContext
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setOnAudioFocusChangeListener { change ->
                // Otra aplicación toma el sonido (una llamada, otro reproductor): se pausa
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    pause(app)
                }
            }
            .build().also { focusRequest = it }
        (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).requestAudioFocus(request)
    }

    fun abandonFocus(context: Context) {
        val request = focusRequest ?: return
        (context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .abandonAudioFocusRequest(request)
    }
}
