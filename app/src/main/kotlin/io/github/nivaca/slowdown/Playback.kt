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

    // Como en py-slowdown: una pausa vuelve adonde empezó a tocar, para oír otra vez el pasaje, y guarda dónde
    // fue, desde donde sigue Seguir desde la pausa (la línea de puntos de la onda)
    var returnTo: Double? = null  // donde empezó a tocar
    var pausedAt: Double? = null  // donde fue la última pausa

    /** Play from the last pause: Play/Pause goes on from the last pause instead of from the position. */
    fun fromPause(context: Context) = prefs(context).getBoolean("play_from_pause", false)

    fun setFromPause(context: Context, on: Boolean) = prefs(context).edit().putBoolean("play_from_pause", on).apply()

    /** Play/Pause's play: from the position, or, with Play from the last pause, from the last pause. */
    fun play(context: Context) {
        if (!loaded || Engine.playing()) return
        if (fromPause(context) && pausedAt != null) {
            continuePlaying(context)
            return
        }
        start(context)
        returnTo = Engine.position()  // (después de tocar: al final del archivo, vuelve a empezar desde 0)
    }

    /** Plays from the last pause; pausing then goes back to where playing first started. */
    fun continuePlaying(context: Context) {
        if (!loaded || Engine.playing()) return
        val paused = pausedAt
        if (paused == null) {
            play(context)
            return
        }
        val first = returnTo
        Engine.seek(minOf(paused, Engine.duration()))
        start(context)
        returnTo = first ?: Engine.position()
    }

    /** Moves the position by hand: while it plays, playing starts again there (a pause goes back there); while
     * paused, with Play from the last pause, it plays from there, not from the old pause. */
    fun seek(context: Context, seconds: Double) {
        if (!loaded) return
        Engine.seek(seconds.coerceIn(0.0, Engine.duration()))
        if (Engine.playing()) returnTo = Engine.position()
        else if (fromPause(context)) pausedAt = null
    }

    fun stop() {
        Engine.stop()
        returnTo = null
        pausedAt = null
    }

    /** Forgets where playing started and was paused (another file). */
    fun forget() {
        returnTo = null
        pausedAt = null
    }

    // Adonde vuelve la posición: donde empezó a tocar; con una selección que se repite, dentro de ella
    private fun returnPoint(): Double? = returnPoint(returnTo, session.selection?.takeIf { session.repeat })

    /** Where a pause takes the position back to: BACK, where playing started; inside LOOP, if one repeats. */
    fun returnPoint(back: Double?, loop: Pair<Double, Double>?): Double? {
        if (back == null || loop == null) return back
        return if (back >= loop.first && back < loop.second) back else loop.first
    }

    /** Whether the end of the file was reached since last asked; the position then also goes back to where
     * playing started. */
    fun takeEnded(): Boolean {
        if (!Engine.takeEnded()) return false
        returnPoint()?.let {
            Engine.seek(it)
            pausedAt = null
        }
        return true
    }

    private fun start(context: Context) {
        requestFocus(context)
        val count = countIn(context)
        if (count.on) {
            val prefs = prefs(context)
            val beats = prefs.getInt("countin_beats", 4).coerceIn(1, CountInRules.MAX_BEATS)
            val follow = prefs.getBoolean("countin_follow", true)
            val tempo = CountInRules.tempo(count.bpm, session.speed, follow)
            // Con un primer pulso, la grabación entra en el pulso más cercano de su rejilla; si no, con el desfase
            val offset = firstBeat()?.let { first ->
                CountInRules.gridOffset(Engine.position(), first, count.bpm.toDouble(), session.speed,
                    Engine.maxEarlier(Engine.rate(), tempo))
            } ?: count.offset
            Engine.play(beats, tempo, offset)
        } else {
            Engine.play(0, 0.0, 0.0)
        }
        PlaybackService.update(context, starting = true)
    }

    // --- la cuenta previa: la del archivo (su .slx), o la de siempre

    fun countIn(context: Context): CountIn = session.countIn ?: prefs(context).let {
        CountIn(it.getBoolean("countin_on", false), it.getInt("countin_bpm", 100), 0.0)
    }

    fun setCountIn(context: Context, count: CountIn) {
        session.countIn = count
        prefs(context).edit().putBoolean("countin_on", count.on).putInt("countin_bpm", count.bpm).apply()
    }

    fun firstBeat(): Double? = session.bookmarks.firstOrNull { it.firstBeat }?.time

    fun pause(context: Context) {
        if (!Engine.playing()) return
        Engine.pause()
        pausedAt = Engine.position()
        returnPoint()?.let { Engine.seek(it) }
        save(context)
        PlaybackService.update(context)
    }

    fun toggle(context: Context) = if (Engine.playing()) pause(context) else play(context)

    fun jump(context: Context, seconds: Double) {
        if (!loaded) return
        seek(context, Engine.position() + seconds)
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
