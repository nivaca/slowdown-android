// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * La ventana de Slowdown, ordenada como la de py-slowdown: la onda arriba; la
 * reproducción y el tiempo; las perillas de la velocidad, el volumen y la
 * altura; la selección; y el panel de los marcadores y los bucles, con sus dos
 * pestañas.
 *
 * Lo que se hace con cada archivo (la posición, la velocidad, la altura, el
 * volumen, la selección, los marcadores y los bucles) se guarda en su .slx
 * (Sidecar.kt) al salir de la ventana, al cerrar el archivo y al cambiar un
 * marcador o un bucle. El último archivo se abre otra vez al volver.
 */
package io.github.nivaca.slowdown

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Choreographer
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : Activity(), WaveformView.Listener {

    private lateinit var prefs: SharedPreferences
    private lateinit var palette: Palette
    private lateinit var waveform: WaveformView
    private lateinit var hint: TextView
    private lateinit var time: TextView
    private lateinit var status: TextView
    private lateinit var playButton: ImageButton
    private lateinit var repeatButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var speedKnob: Knob
    private lateinit var volumeKnob: Knob
    private lateinit var semitoneKnob: Knob
    private lateinit var centKnob: Knob
    private lateinit var boost: CheckBox
    private lateinit var originalPitch: Button
    private lateinit var selectionLabel: TextView
    private lateinit var tabBookmarks: TextView
    private lateinit var tabLoops: TextView
    private lateinit var headerTime: TextView
    private lateinit var list: ListView
    private lateinit var exportButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var uri: Uri? = null
    private var source: Source? = null
    private var session = Session()
    private var loaded = false
    private var opening = false
    private var menu: Menu? = null
    private var skip = 5
    private var showingLoops = false
    private var chosen = -1  // la fila elegida de la tabla

    // La carpeta de los .slx, dentro de la aplicación; y, si se eligió, otra (la de la música, por ejemplo), que
    // se lee primero y se escribe también
    private val slxFolder by lazy { File(filesDir, "slx") }

    private fun chosenFolder(): TreeFolder? {
        val tree = prefs.getString("slx_tree", null)?.let(Uri::parse) ?: return null
        val allowed = contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        return if (allowed) TreeFolder(contentResolver, tree) else null
    }

    private fun stores(): List<SlxFolder> = listOfNotNull(chosenFolder(), FileFolder(slxFolder))

    private val version by lazy { packageManager.getPackageInfo(packageName, 0).versionName ?: "" }

    // --- el ciclo de la ventana

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.main)
        setActionBar(findViewById(R.id.toolbar))
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        palette = Palette(this)
        skip = prefs.getInt("skip", 5)
        bind()
        // De borde a borde (obligatorio desde Android 15): nada queda bajo la barra de estado ni la de navegación
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        Engine.setQuality(prefs.getInt("quality", Engine.QUALITY_HIGH))
        val incoming = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        val last = prefs.getString("last", null)?.let(Uri::parse)
        when {
            incoming != null -> open(incoming)
            last != null && hasPermission(last) -> open(last)
            else -> showEmpty()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let { open(it) }
    }

    override fun onResume() {
        super.onResume()
        Choreographer.getInstance().postFrameCallback(ticker)
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onPause() {
        super.onPause()
        Choreographer.getInstance().removeFrameCallback(ticker)
        unregisterReceiver(noisy)
        save()
    }

    override fun onStop() {
        super.onStop()
        // Sin un servicio de reproducción, Android congela la aplicación al rato de dejar la pantalla: mejor
        // pausar ahora que cortar en cualquier parte. (Tocar con la pantalla apagada vendrá con ese servicio.)
        if (Engine.playing()) {
            Engine.pause()
            showPlaying()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            Engine.pause()
            Engine.nativeClose()
            abandonFocus()
        }
    }

    // --- los mandos

    private fun bind() {
        waveform = findViewById(R.id.waveform)
        waveform.listener = this
        hint = findViewById(R.id.hint)
        time = findViewById(R.id.time)
        status = findViewById(R.id.status)
        playButton = findViewById(R.id.play)
        repeatButton = findViewById(R.id.repeat)
        backButton = findViewById(R.id.back)
        forwardButton = findViewById(R.id.forward)
        speedKnob = findViewById(R.id.speed)
        volumeKnob = findViewById(R.id.volume)
        semitoneKnob = findViewById(R.id.semitones)
        centKnob = findViewById(R.id.cents)
        boost = findViewById(R.id.boost)
        originalPitch = findViewById(R.id.original_pitch)
        selectionLabel = findViewById(R.id.selection)
        time.typeface = android.graphics.Typeface.MONOSPACE  // (el tipo del teléfono puede cambiar «monospace»)
        tabBookmarks = findViewById(R.id.tab_bookmarks)
        tabLoops = findViewById(R.id.tab_loops)
        headerTime = findViewById(R.id.header_time)
        list = findViewById(R.id.list)
        exportButton = findViewById(R.id.export)

        playButton.setOnClickListener { toggle() }
        findViewById<View>(R.id.stop).setOnClickListener {
            Engine.stop()
            showPlaying()
        }
        findViewById<View>(R.id.to_start).setOnClickListener { seek(0.0) }
        findViewById<View>(R.id.to_end).setOnClickListener { seek(Engine.duration()) }
        repeating(backButton) { jump(-skip.toDouble()) }
        repeating(forwardButton) { jump(skip.toDouble()) }
        updateSkipDescriptions()
        repeatButton.setOnClickListener { setRepeat(!session.repeat) }

        // La velocidad, en por ciento, con 100 % arriba: de 25 a 100 a la izquierda, de 100 a 300 a la derecha
        speedKnob.apply {
            minimum = (Engine.MIN_SPEED * 100).roundToInt()
            maximum = (Engine.MAX_SPEED * 100).roundToInt()
            top = 100
            home = 100
            ticks = SPEED_TICKS
            format = { "$it%" }
            show(100)
            onChange = { setSpeed(it / 100.0, fromKnob = true) }
        }
        findViewById<View>(R.id.normal_speed).setOnClickListener { setSpeed(1.0) }
        findViewById<View>(R.id.speeds).setOnClickListener { speedMenu(it) }

        // El volumen: hasta 100 % (el sonido de la grabación); con el refuerzo, por 1,5, en rojo donde lo pasa
        volumeKnob.apply {
            minimum = 0
            maximum = NORMAL_VOLUME
            home = NORMAL_VOLUME
            ticks = VOLUME_TICKS
            format = { "$it%" }
            show(NORMAL_VOLUME)
            onChange = { setVolume(it) }
        }
        boost.isChecked = prefs.getBoolean("volume_boost", false)
        boost.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("volume_boost", on).apply()
            applyVolume()
        }

        // La altura: semitonos y cents, cada uno con su perilla, 0 arriba
        semitoneKnob.apply {
            minimum = -Engine.MAX_PITCH.toInt()
            maximum = Engine.MAX_PITCH.toInt()
            top = 0
            ticks = (minimum..maximum).filter { it % SEMITONE_TICK == 0 || it == minimum || it == maximum }
            format = signed(getString(R.string.st))
            show(0)
            onChange = { setPitch(it, centKnob.value) }
        }
        centKnob.apply {
            minimum = -MAX_CENTS
            maximum = MAX_CENTS
            top = 0
            ticks = (minimum..maximum).filter { it % CENT_TICK == 0 }
            format = signed(" ¢")
            show(0)
            onChange = { setPitch(semitoneKnob.value, it) }
        }
        originalPitch.setOnClickListener { setPitch(0, 0) }

        findViewById<View>(R.id.mark_a).setOnClickListener { selectionEdge(start = true) }
        findViewById<View>(R.id.mark_b).setOnClickListener { selectionEdge(start = false) }
        findViewById<View>(R.id.clear).setOnClickListener { setSelection(null) }

        // El panel de los marcadores y los bucles
        tabBookmarks.setOnClickListener { showTab(loops = false) }
        tabLoops.setOnClickListener { showTab(loops = true) }
        findViewById<View>(R.id.table_header).setBackgroundColor(palette.ruler)
        findViewById<View>(R.id.table).setBackgroundColor(palette.base)
        list.adapter = table
        list.setOnItemClickListener { _, _, position, _ -> choose(position) }
        list.setOnItemLongClickListener { _, _, position, _ ->
            choose(position)
            edit(position)
            true
        }
        findViewById<View>(R.id.add).setOnClickListener {
            if (showingLoops) saveLoop() else if (loaded) addBookmark(Engine.position())
        }
        findViewById<View>(R.id.remove).setOnClickListener { removeChosen() }
        exportButton.setOnClickListener { exportBookmarks() }
        showTab(loops = false)

        for (id in listOf(R.id.to_start, R.id.back, R.id.play, R.id.stop, R.id.forward, R.id.to_end, R.id.repeat,
            R.id.normal_speed, R.id.speeds, R.id.original_pitch, R.id.mark_a, R.id.mark_b, R.id.clear)) {
            val view = findViewById<View>(id)
            view.tooltipText = view.contentDescription ?: (view as? TextView)?.text
        }
        for (id in listOf(R.id.mark_a, R.id.mark_b)) {
            val view = findViewById<View>(id)
            view.tooltipText = "${view.contentDescription}. ${getString(R.string.no_selection)}"
        }
    }

    private fun signed(unit: String): (Int) -> String = { value ->
        val sign = if (value > 0) "+" else if (value < 0) "−" else ""
        "$sign${kotlin.math.abs(value)}$unit"
    }

    // Un botón que, mantenido, se repite (como en el ordenador)
    private fun repeating(button: View, action: () -> Unit) {
        val again = object : Runnable {
            override fun run() {
                action()
                handler.postDelayed(this, 300)
            }
        }
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.isPressed = true
                    action()
                    handler.postDelayed(again, 500)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    handler.removeCallbacks(again)
                    if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
                }
            }
            true
        }
    }

    private fun updateSkipDescriptions() {
        backButton.contentDescription = getString(R.string.back, skip)
        forwardButton.contentDescription = getString(R.string.forward, skip)
        backButton.tooltipText = backButton.contentDescription
        forwardButton.tooltipText = forwardButton.contentDescription
    }

    // --- abrir y cerrar

    private fun hasPermission(uri: Uri) =
        uri.scheme == "file" || contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    private fun pick() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*", "application/ogg"))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(request, result, data)
        if (result != RESULT_OK) return
        val target = data?.data ?: return
        when (request) {
            PICK -> {
                try {
                    contentResolver.takePersistableUriPermission(target, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // (algunos proveedores no lo permiten: se puede tocar igual, pero no reabrir)
                }
                open(target)
            }
            EXPORT -> writeBookmarks(target)
            EXPORT_SLX -> writeSlx(target)
            IMPORT_SLX -> readSlx(target)
            TREE -> {
                contentResolver.takePersistableUriPermission(target,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                prefs.edit().putString("slx_tree", target.toString()).apply()
                save(announce = true)
            }
        }
    }

    private fun describe(uri: Uri): Source {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
        var size = -1L
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)?.let { name = it }
                        if (!cursor.isNull(1)) size = cursor.getLong(1)
                    }
                }
        } catch (_: Exception) {
        }
        return Source(uri.toString(), name, size)
    }

    private fun open(target: Uri) {
        if (opening) return
        save()
        Engine.pause()
        opening = true
        loaded = false
        val described = describe(target)
        title = described.name
        hint.text = getString(R.string.reading, described.name)
        hint.visibility = View.VISIBLE
        thread(name = "open") {
            val error = try {
                contentResolver.openAssetFileDescriptor(target, "r").use { afd ->
                    if (afd == null) "unreadable"
                    else Engine.nativeOpen(afd.parcelFileDescriptor.fd, afd.startOffset, afd.declaredLength)
                }
            } catch (e: Exception) {
                e.message ?: e.javaClass.simpleName
            }
            val kept = stores().firstNotNullOfOrNull { folder ->
                try { Sidecar.load(described, folder) } catch (_: Exception) { null }
            }
            handler.post {
                opening = false
                if (error != null) {
                    showEmpty()
                    val text = if (error == "output") getString(R.string.cannot_play, described.name)
                    else getString(R.string.cannot_read, described.name, error)
                    Toast.makeText(this, text, Toast.LENGTH_LONG).show()
                    if (prefs.getString("last", null) == target.toString()) prefs.edit().remove("last").apply()
                    return@post
                }
                uri = target
                source = described
                prefs.edit().putString("last", target.toString()).apply()
                loaded(kept ?: Session())
            }
        }
    }

    private fun loaded(kept: Session) {
        loaded = true
        session = kept
        hint.visibility = View.GONE
        waveform.rate = Engine.rate()
        waveform.duration = 0.0
        waveform.duration = Engine.duration()
        Engine.setFormants(session.formants)
        session.speed = Engine.clampSpeed(session.speed)
        Engine.setSpeed(session.speed)
        session.pitch = session.pitch.coerceIn(-Engine.MAX_PITCH, Engine.MAX_PITCH)
        Engine.setPitch(session.pitch)
        session.volume?.let { kept ->
            if (kept > NORMAL_VOLUME) {
                // (de py-slowdown 1.3, más fuerte que la grabación: con el refuerzo, lo que suena casi igual)
                boost.isChecked = true
                session.volume = minOf(NORMAL_VOLUME, (kept / sqrt(boostFactor())).roundToInt())
            }
        }
        volumeKnob.show(session.volume ?: NORMAL_VOLUME)
        applyVolume()
        setSelection(session.selection, save = false)
        if (session.position > 0) Engine.seek(session.position)
        waveform.update(session.position)
        chosen = -1
        showSpeed()
        showPitch()
        showMarks()
        updateMenu()
    }

    private fun showEmpty() {
        loaded = false
        title = getString(R.string.app_name)
        hint.text = getString(R.string.open_hint)
        hint.visibility = View.VISIBLE
        waveform.duration = 0.0
        waveform.bookmarks = emptyList()
        waveform.selection = null
        time.text = "0:00.0 / 0:00.0"
        status.text = ""
        session = Session()
        chosen = -1
        showSpeed()
        showPitch()
        showMarks()
        showSelection()
        updateMenu()
    }

    private fun closeFile() {
        save()
        Engine.pause()
        Engine.nativeClose()
        uri = null
        source = null
        prefs.edit().remove("last").apply()
        showEmpty()
    }

    private fun save(announce: Boolean = false) {
        val source = source ?: return
        if (!loaded) return
        session.position = Engine.position()
        // En la carpeta elegida, si la hay, y siempre en la de la aplicación
        var shown: String? = null
        for (folder in stores()) {
            try {
                val name = Sidecar.save(session, source, folder, version)
                if (shown == null) shown = if (folder is TreeFolder) "${folder.label()}/$name" else name
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: e.toString(), Toast.LENGTH_LONG).show()
            }
        }
        if (announce && shown != null) Toast.makeText(this, getString(R.string.saved, shown), Toast.LENGTH_SHORT).show()
    }

    // --- tocar

    private fun toggle() {
        if (!loaded) {
            pick()
            return
        }
        if (Engine.playing()) {
            Engine.pause()
        } else {
            requestFocus()
            Engine.play(0, 0.0, 0.0)
        }
        showPlaying()
    }

    private fun seek(seconds: Double) {
        if (!loaded) return
        Engine.seek(seconds.coerceIn(0.0, Engine.duration()))
        waveform.update(Engine.position())
    }

    private fun jump(seconds: Double) {
        if (loaded) seek(Engine.position() + seconds)
    }

    private fun setSpeed(speed: Double, fromKnob: Boolean = false) {
        session.speed = Engine.clampSpeed(Math.round(speed * 100) / 100.0)
        Engine.setSpeed(session.speed)
        if (!fromKnob) showSpeed()
    }

    private fun showSpeed() {
        speedKnob.show((session.speed * 100).roundToInt())
    }

    private fun speedMenu(anchor: View) {
        // Las velocidades de siempre, con la de ahora marcada si está entre ellas
        val popup = PopupMenu(this, anchor)
        val now = (session.speed * 100).roundToInt()
        for ((i, percent) in SPEED_PRESETS.withIndex()) {
            popup.menu.add(0, i, i, "$percent%").apply {
                isCheckable = true
                isChecked = percent == now
            }
        }
        popup.setOnMenuItemClickListener { item ->
            setSpeed(SPEED_PRESETS[item.itemId] / 100.0)
            true
        }
        popup.show()
    }

    private fun setPitch(semitones: Int, cents: Int) {
        val whole = semitones.coerceIn(-Engine.MAX_PITCH.toInt(), Engine.MAX_PITCH.toInt())
        session.pitch = (whole + cents.coerceIn(-MAX_CENTS, MAX_CENTS) / 100.0).coerceIn(-Engine.MAX_PITCH, Engine.MAX_PITCH)
        Engine.setPitch(session.pitch)
        showPitch()
    }

    private fun showPitch() {
        // Los semitonos y los cents: los cents como decimales de la altura (−2,30: −2 st, −30 ¢)
        val whole = session.pitch.toInt()
        val cents = Math.round((session.pitch - whole) * 100).toInt()
        semitoneKnob.show(whole)
        centKnob.show(cents)
        originalPitch.isEnabled = session.pitch != 0.0
    }

    private fun setVolume(value: Int) {
        session.volume = value
        applyVolume()
    }

    // Por cuánto multiplica el sonido el refuerzo: uno de BOOSTS, elegido en el menú (como en py-slowdown)
    private fun boostFactor(): Double = prefs.getFloat("volume_boost_factor", BOOST.toFloat()).toDouble()
        .let { saved -> BOOSTS.firstOrNull { kotlin.math.abs(it - saved) < 1e-6 } ?: BOOST }

    private fun setBoostFactor(factor: Double) {
        prefs.edit().putFloat("volume_boost_factor", factor.toFloat()).apply()
        applyVolume()
        updateMenu()
    }

    private fun gain(value: Int): Double =
        // Una curva cuadrada (los pasos de la perilla suenan más parejos al oído), por el refuerzo si está puesto
        (value / 100.0) * (value / 100.0) * (if (boost.isChecked) boostFactor() else 1.0)

    private fun applyVolume() {
        val value = volumeKnob.value
        val factor = boostFactor()
        Engine.setVolume(gain(value))
        // (en rojo desde el primer valor que, con el refuerzo, pasa del sonido de la grabación: 75 % con ×1,8)
        volumeKnob.warn = if (boost.isChecked) ceil(100 / sqrt(factor)).toInt() - 1 else null
        var tip = getString(R.string.volume_value, value)
        if (boost.isChecked) tip += " " + getString(R.string.boosted, number(factor))
        if (gain(value) > 1.0) tip += " " + getString(R.string.louder)
        volumeKnob.tooltipText = tip + "; " + getString(R.string.volume_home)
        boost.tooltipText = getString(R.string.boost_hint, number(factor))
    }

    private fun showPlaying() {
        val playing = Engine.playing()
        playButton.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        playButton.contentDescription = getString(if (playing) R.string.pause else R.string.play)
        // Mientras suena, la pantalla no se apaga (se practica mirándola)
        if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // Cada cuadro de la pantalla: la posición, el tiempo y el final
    private val ticker = object : Choreographer.FrameCallback {
        private var wasPlaying = false
        private var decoding = false

        override fun doFrame(frameTimeNanos: Long) {
            if (loaded) {
                val here = Engine.position()
                waveform.update(here)
                time.text = "${TimeFormat.long(here)} / ${TimeFormat.long(Engine.duration())}"
                val done = Engine.done()
                if (!done || decoding) {
                    decoding = !done
                    waveform.duration = Engine.duration()
                    val total = Engine.duration() * Engine.rate()
                    status.text = if (done) "" else "${(100 * Engine.decoded() / maxOf(1.0, total)).roundToInt()} %"
                    if (done) applyRepeat()  // (el archivo entero se repite cuando está leído hasta su final)
                }
                if (Engine.takeEnded() || wasPlaying != Engine.playing()) {
                    wasPlaying = Engine.playing()
                    showPlaying()
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // --- la selección y la repetición

    private fun selectionEdge(start: Boolean) {
        // A: la selección empieza aquí (y acaba donde acababa, o al final); B: acaba aquí
        if (!loaded) return
        val here = Engine.position()
        val current = session.selection
        val next = if (start) here to (current?.second?.takeIf { it > here } ?: Engine.duration())
        else (current?.first?.takeIf { it < here } ?: 0.0) to here
        setSelection(if (next.second - next.first > 0.05) next else null)
    }

    private fun setSelection(selection: Pair<Double, Double>?, save: Boolean = true) {
        session.selection = selection
        waveform.selection = selection
        applyRepeat()
        showSelection()
        if (save) save()
    }

    private fun setRepeat(on: Boolean) {
        session.repeat = on
        if (on && session.selection == null && loaded) {
            Toast.makeText(this, R.string.repeat_whole, Toast.LENGTH_SHORT).show()
        }
        applyRepeat()
    }

    private fun applyRepeat() {
        // La selección, o el archivo entero si no hay (cuando esté leído hasta su final)
        val selection = session.selection
        if (loaded) {
            when {
                session.repeat && selection != null -> Engine.setLoop(selection.first, selection.second)
                session.repeat && Engine.done() -> Engine.setLoop(0.0, Engine.duration())
                Engine.looping() && !session.repeat -> Engine.setLoop(-1.0, -1.0)
            }
        }
        waveform.repeating = session.repeat && selection != null
        repeatButton.isActivated = session.repeat
        repeatButton.imageTintList = if (session.repeat) ColorStateList.valueOf(palette.wave) else null
        repeatButton.setBackgroundColor(if (session.repeat) (palette.wave and 0x00ffffff) or 0x33000000 else Color.TRANSPARENT)
    }

    private fun showSelection() {
        val selection = session.selection
        selectionLabel.text = if (selection == null) getString(R.string.selection_none)
        else getString(R.string.selection, TimeFormat.long(selection.first), TimeFormat.long(selection.second))
    }

    override fun onSelect(start: Double, end: Double) {
        setSelection(start to end)
    }

    override fun onSeek(seconds: Double) {
        seek(seconds)
    }

    override fun onBookmarkAt(seconds: Double) {
        addBookmark(seconds)
    }

    // --- el panel de los marcadores y los bucles

    private fun showTab(loops: Boolean) {
        showingLoops = loops
        chosen = -1
        for ((tab, on) in listOf(tabBookmarks to !loops, tabLoops to loops)) {
            tab.setTextColor(if (on) palette.text else palette.faint)
            tab.setBackgroundColor(if (on) palette.base else Color.TRANSPARENT)
            tab.paint.isFakeBoldText = on
            tab.invalidate()
        }
        headerTime.text = getString(R.string.time)
        exportButton.isEnabled = !loops
        table.notifyDataSetChanged()
    }

    private fun choose(position: Int) {
        chosen = position
        table.notifyDataSetChanged()
        if (showingLoops) {
            // Un bucle: se elige su trecho y se repite
            val loop = sortedLoops().getOrNull(position) ?: return
            setSelection(loop.start to loop.end)
            setRepeat(true)
            seek(loop.start)
        } else {
            session.bookmarks.getOrNull(position)?.let { seek(it.time) }
        }
    }

    private fun sortedLoops() = session.loops.sortedWith(compareBy({ it.start }, { it.end }))

    private val table = object : BaseAdapter() {
        override fun getCount() = if (showingLoops) session.loops.size else session.bookmarks.size
        override fun getItem(position: Int): Any = if (showingLoops) sortedLoops()[position] else session.bookmarks[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = dp(40)
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    fontFeatureSettings = "tnum"
                    setPadding(dp(6), 0, dp(10), 0)
                })
                addView(TextView(context).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            val left = row.getChildAt(0) as TextView
            val right = row.getChildAt(1) as TextView
            if (showingLoops) {
                val loop = sortedLoops()[position]
                left.text = "${TimeFormat.long(loop.start)} – ${TimeFormat.long(loop.end)}"
                right.text = loop.label
            } else {
                val mark = session.bookmarks[position]
                left.text = TimeFormat.long(mark.time)
                right.text = if (mark.firstBeat) (if (mark.label.isEmpty()) "1" else "1 · ${mark.label}") else mark.label
            }
            // La fila elegida, como en el ordenador: con el color del resaltado y el texto en blanco
            val on = position == chosen
            row.setBackgroundColor(if (on) palette.wave else Color.TRANSPARENT)
            val ink = if (on) Color.WHITE else palette.text
            left.setTextColor(ink)
            right.setTextColor(ink)
            return row
        }
    }

    private fun ask(title: String, initial: String, done: (String) -> Unit) {
        val field = EditText(this).apply {
            setText(initial)
            setSelection(initial.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isSingleLine = true
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ -> done(field.text.toString().trim()) }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
        field.requestFocus()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun changed() {
        showMarks()
        save()
    }

    private fun addBookmark(seconds: Double) {
        if (!loaded) return
        val mark = Bookmark(Math.round(seconds * 1000) / 1000.0)
        session.bookmarks.add(mark)
        if (showingLoops) showTab(loops = false)
        changed()
        chosen = session.bookmarks.indexOf(mark)
        table.notifyDataSetChanged()
        list.smoothScrollToPosition(chosen)
        ask(getString(R.string.bookmark_at, TimeFormat.long(mark.time)), "") { label ->
            mark.label = label
            changed()
        }
    }

    private fun edit(position: Int) {
        if (showingLoops) {
            val loop = sortedLoops().getOrNull(position) ?: return
            ask(getString(R.string.loop_name, TimeFormat.long(loop.start), TimeFormat.long(loop.end)), loop.label) {
                loop.label = it
                changed()
            }
            return
        }
        val mark = session.bookmarks.getOrNull(position) ?: return
        val items = arrayOf(getString(R.string.rename), getString(R.string.first_beat), getString(R.string.remove))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.bookmark_at, TimeFormat.long(mark.time)))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> ask(getString(R.string.bookmark_at, TimeFormat.long(mark.time)), mark.label) {
                        mark.label = it
                        changed()
                    }
                    1 -> {
                        // (uno solo por archivo)
                        val was = mark.firstBeat
                        session.bookmarks.forEach { it.firstBeat = false }
                        mark.firstBeat = !was
                        changed()
                    }
                    2 -> {
                        session.bookmarks.remove(mark)
                        chosen = -1
                        changed()
                    }
                }
            }
            .show()
    }

    private fun removeChosen() {
        if (chosen < 0) return
        if (showingLoops) sortedLoops().getOrNull(chosen)?.let { session.loops.remove(it) }
        else session.bookmarks.getOrNull(chosen)?.let { session.bookmarks.remove(it) }
        chosen = -1
        changed()
    }

    private fun saveLoop() {
        val selection = session.selection
        if (!loaded || selection == null) {
            Toast.makeText(this, R.string.no_selection, Toast.LENGTH_SHORT).show()
            return
        }
        val (a, b) = selection
        ask(getString(R.string.loop_name, TimeFormat.long(a), TimeFormat.long(b)), "") { label ->
            session.loops.add(Loop(Math.round(a * 1000) / 1000.0, Math.round(b * 1000) / 1000.0, label))
            if (!showingLoops) showTab(loops = true)
            changed()
        }
    }

    private fun showMarks() {
        session.bookmarks.sortBy { it.time }
        waveform.bookmarks = session.bookmarks.toList()
        if (chosen >= table.count) chosen = -1
        table.notifyDataSetChanged()
    }

    // Los marcadores como texto, uno por línea: el tiempo, un tabulador y el nombre (como py-slowdown)
    private fun exportBookmarks() {
        val source = source ?: return
        if (session.bookmarks.isEmpty()) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, source.name.substringBeforeLast('.') + ".bookmarks.txt")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, EXPORT)
    }

    private fun writeBookmarks(target: Uri) {
        val source = source ?: return
        try {
            contentResolver.openOutputStream(target, "wt")?.use {
                it.write(exportText(session.bookmarks, source.name).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, getString(R.string.saved, describe(target).name), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: e.toString(), Toast.LENGTH_LONG).show()
        }
    }

    // --- el menú

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        this.menu = menu
        updateMenu()
        return true
    }

    private fun updateMenu() {
        val menu = menu ?: return
        menu.findItem(R.id.formants).isChecked = session.formants
        val quality = prefs.getInt("quality", Engine.QUALITY_HIGH)
        menu.findItem(when (quality) {
            Engine.QUALITY_LIGHT -> R.id.quality_light
            Engine.QUALITY_R2 -> R.id.quality_r2
            else -> R.id.quality_high
        }).isChecked = true
        for (id in listOf(R.id.save, R.id.close, R.id.export_slx, R.id.import_slx)) menu.findItem(id).isEnabled = loaded
        // El refuerzo: ×1,2 … ×3, el de ahora marcado
        val boosts = menu.findItem(R.id.boost_factor).subMenu ?: return
        if (boosts.size() == 0) {
            BOOSTS.forEachIndexed { i, factor -> boosts.add(BOOST_GROUP, BOOST_ITEM + i, i, "×${number(factor)}") }
            boosts.setGroupCheckable(BOOST_GROUP, true, true)
        }
        val factor = boostFactor()
        // (solo el de ahora: en un grupo exclusivo, setChecked(false) también marca la opción)
        boosts.findItem(BOOST_ITEM + BOOSTS.indexOf(factor))?.isChecked = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.open -> pick()
            R.id.formants -> {
                session.formants = !session.formants
                Engine.setFormants(session.formants)
                updateMenu()
            }
            R.id.quality_high, R.id.quality_light, R.id.quality_r2 -> {
                val quality = when (item.itemId) {
                    R.id.quality_light -> Engine.QUALITY_LIGHT
                    R.id.quality_r2 -> Engine.QUALITY_R2
                    else -> Engine.QUALITY_HIGH
                }
                prefs.edit().putInt("quality", quality).apply()
                Engine.setQuality(quality)
                updateMenu()
            }
            in BOOST_ITEM until BOOST_ITEM + BOOSTS.size -> setBoostFactor(BOOSTS[item.itemId - BOOST_ITEM])
            R.id.skip -> {
                val choices = intArrayOf(1, 2, 3, 5, 10, 15, 30, 60)
                AlertDialog.Builder(this)
                    .setTitle(R.string.skip_length)
                    .setSingleChoiceItems(choices.map { getString(R.string.seconds, it) }.toTypedArray(),
                        choices.indexOf(skip)) { dialog, which ->
                        skip = choices[which]
                        prefs.edit().putInt("skip", skip).apply()
                        updateSkipDescriptions()
                        dialog.dismiss()
                    }
                    .show()
            }
            R.id.save -> save(announce = true)
            R.id.close -> closeFile()
            R.id.licenses -> showLicenses()
            R.id.export_slx -> exportSlx()
            R.id.import_slx -> importSlx()
            R.id.slx_folder -> slxFolderDialog()
            R.id.about -> AlertDialog.Builder(this)
                .setTitle(R.string.about)
                .setMessage(getString(R.string.about_text, version))
                .setPositiveButton(R.string.ok, null)
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    // --- el .slx fuera de la aplicación: exportarlo, cargar uno, y la carpeta donde se guardan

    private fun exportSlx() {
        val source = source ?: return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = TreeFolder.MIME
            putExtra(Intent.EXTRA_TITLE, Sidecar.names(source).first())  // (canción.slx, como en py-slowdown)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, EXPORT_SLX)
    }

    private fun writeSlx(target: Uri) {
        val source = source ?: return
        session.position = Engine.position()
        try {
            contentResolver.openOutputStream(target, "wt")?.use {
                it.write(Sidecar.format(session, source, version).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, getString(R.string.saved, describe(target).name), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: e.toString(), Toast.LENGTH_LONG).show()
        }
    }

    private fun importSlx() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, IMPORT_SLX)
    }

    private fun readSlx(target: Uri) {
        val source = source ?: return
        val text = try {
            contentResolver.openInputStream(target)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) {
            null
        }
        val kept = text?.let { Sidecar.read(it) }
        if (kept == null) {
            Toast.makeText(this, getString(R.string.not_slx, describe(target).name), Toast.LENGTH_LONG).show()
            return
        }
        val apply = {
            loaded(kept)
            save(announce = true)
        }
        // Si es de otra grabación (por su nombre), se pregunta antes
        val of = Sidecar.owner(text)?.second.orEmpty()
        if (of.isEmpty() || of == source.name) apply()
        else AlertDialog.Builder(this)
            .setMessage(getString(R.string.slx_of_other, of, source.name))
            .setPositiveButton(R.string.ok) { _, _ -> apply() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun slxFolderDialog() {
        val chosen = chosenFolder()
        val items = arrayOf(getString(R.string.slx_in_app),
            getString(R.string.slx_choose) + (chosen?.let { " (${it.label()})" } ?: ""))
        AlertDialog.Builder(this)
            .setTitle(R.string.slx_folder_title)
            .setSingleChoiceItems(items, if (chosen == null) 0 else 1) { dialog, which ->
                dialog.dismiss()
                if (which == 0) {
                    // (y deja el permiso sobre la carpeta, que ya no hace falta)
                    prefs.getString("slx_tree", null)?.let { tree ->
                        try {
                            contentResolver.releasePersistableUriPermission(Uri.parse(tree),
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        } catch (_: SecurityException) {
                        }
                    }
                    prefs.edit().remove("slx_tree").apply()
                } else {
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), TREE)
                }
            }
            .show()
    }

    // --- las licencias: lo que lleva el APK, y el texto de cada una (de sus assets)

    private fun showLicenses() {
        val rows = Licenses.components.map { c ->
            val version = if (c.name == "Slowdown") version else c.version
            val title = if (version.isEmpty()) c.name else "${c.name} $version"
            "$title\n${c.license} · ${c.holder}"
        }.toTypedArray()
        val header = TextView(this).apply {
            text = getString(R.string.licenses_intro)
            setPadding(dp(24), dp(12), dp(24), dp(4))
        }
        AlertDialog.Builder(this)
            .setCustomTitle(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = getString(R.string.licenses)
                    setTextAppearance(android.R.style.TextAppearance_DeviceDefault_DialogWindowTitle)
                    setPadding(dp(24), dp(20), dp(24), 0)
                })
                addView(header)
            })
            .setItems(rows) { _, which -> showLicense(Licenses.components[which]) }
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showLicense(component: Component) {
        val text = component.files.joinToString("\n\n" + "=".repeat(40) + "\n\n") { name ->
            try {
                Licenses.reflow(assets.open(name).bufferedReader().use { it.readText() })
            } catch (e: java.io.IOException) {
                name
            }
        }
        val body = TextView(this).apply {
            this.text = "${component.name}\n${component.holder}\n${component.license}\n${component.url}\n\n$text"
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle(component.license)
            .setView(android.widget.ScrollView(this).apply { addView(body) })
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    // --- el teclado (también un pedal que manda teclas) y los botones de los auriculares

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!loaded) return super.onKeyDown(keyCode, event)
        val shift = event.isShiftPressed
        val ctrl = event.isCtrlPressed
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> toggle()
            KeyEvent.KEYCODE_MEDIA_PLAY -> if (!Engine.playing()) toggle()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> if (Engine.playing()) toggle()
            KeyEvent.KEYCODE_DPAD_LEFT -> when {
                ctrl -> nextBookmark(-1)
                shift -> jump(-1.0)
                else -> jump(-skip.toDouble())
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> when {
                ctrl -> nextBookmark(1)
                shift -> jump(1.0)
                else -> jump(skip.toDouble())
            }
            KeyEvent.KEYCODE_PAGE_UP -> nextBookmark(-1)
            KeyEvent.KEYCODE_PAGE_DOWN -> nextBookmark(1)
            KeyEvent.KEYCODE_MOVE_HOME -> seek(0.0)
            KeyEvent.KEYCODE_MOVE_END -> seek(Engine.duration())
            KeyEvent.KEYCODE_DPAD_UP -> if (ctrl) setPitch(semitoneKnob.value + 1, centKnob.value) else setSpeed(session.speed + SPEED_STEP)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (ctrl) setPitch(semitoneKnob.value - 1, centKnob.value) else setSpeed(session.speed - SPEED_STEP)
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS -> setSpeed(session.speed + SPEED_STEP)
            KeyEvent.KEYCODE_MINUS -> setSpeed(session.speed - SPEED_STEP)
            KeyEvent.KEYCODE_N -> if (shift) setPitch(0, 0) else setSpeed(1.0)
            KeyEvent.KEYCODE_S -> { Engine.stop(); showPlaying() }
            KeyEvent.KEYCODE_B -> addBookmark(Engine.position())
            KeyEvent.KEYCODE_I -> selectionEdge(start = true)
            KeyEvent.KEYCODE_O -> selectionEdge(start = false)
            KeyEvent.KEYCODE_L -> if (shift) saveLoop() else setRepeat(!session.repeat)
            KeyEvent.KEYCODE_ESCAPE -> setSelection(null)
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun nextBookmark(direction: Int) {
        val here = Engine.position()
        val marks = session.bookmarks.map { it.time }.sorted()
        val target = if (direction > 0) marks.firstOrNull { it > here + 0.05 }
        else marks.lastOrNull { it < here - 0.5 } ?: 0.0
        target?.let { seek(it) }
    }

    // --- el sonido de los demás: el foco de audio y los auriculares que se desconectan

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    Engine.pause()
                    showPlaying()
                }
            }
            .build()
    }

    private fun requestFocus() {
        audioManager.requestAudioFocus(focusRequest)
    }

    private fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Se desconectaron los auriculares: no sigue sonando por el altavoz
            if (Engine.playing()) {
                Engine.pause()
                showPlaying()
            }
        }
    }

    companion object {
        private const val PICK = 1
        private const val EXPORT = 2
        private const val EXPORT_SLX = 3
        private const val IMPORT_SLX = 4
        private const val TREE = 5
        const val SPEED_STEP = 0.05
        const val NORMAL_VOLUME = 100  // por ciento: el sonido de la grabación, lo más que da la perilla
        val BOOSTS = listOf(1.2, 1.5, 1.8, 2.0, 2.5, 3.0)  // por cuánto puede multiplicar el sonido el refuerzo
        const val BOOST = 1.8  // el de siempre; la perilla no cambia de número
        private const val BOOST_GROUP = 10
        private const val BOOST_ITEM = 100

        /** VALUE as the language of the phone writes it (1,8 in Spanish), without needless decimals. */
        fun number(value: Double, locale: java.util.Locale = java.util.Locale.getDefault()): String =
            java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(value)
        const val MAX_CENTS = 50
        const val SEMITONE_TICK = 3
        const val CENT_TICK = 5
        val SPEED_TICKS = listOf(25, 50, 75, 100, 150, 200, 250, 300)  // alrededor de la perilla de la velocidad
        val VOLUME_TICKS = listOf(0, 25, 50, 75, 100)
        val SPEED_PRESETS = listOf(50, 60, 70, 75, 80, 90, 100, 110, 125, 150)  // las de py-slowdown

        /** The bookmarks as plain text, one per line: time, a tab, the label (as py-slowdown exports them). */
        fun exportText(bookmarks: List<Bookmark>, audioName: String): String =
            (listOf("# $audioName") + bookmarks.sortedBy { it.time }.map { "${TimeFormat.long(it.time)}\t${it.label}" })
                .joinToString("\n") + "\n"
    }
}
