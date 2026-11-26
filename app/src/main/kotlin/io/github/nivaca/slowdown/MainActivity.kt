// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * La ventana de Slowdown, ordenada como la de py-slowdown: la onda arriba; la
 * reproducción y el tiempo; las perillas de la velocidad, el volumen y la
 * altura; la selección; y el panel de los marcadores, los bucles y el sonido
 * (los canales, el filtro y el ecualizador), con sus tres pestañas.
 *
 * Lo que se hace con cada archivo (la posición, la velocidad, la altura, el
 * volumen, el ecualizador, la selección, los marcadores y los bucles) se guarda en su .slx
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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.Spinner
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
    private lateinit var countButton: ImageButton
    private val tapTempo = TapTempo()
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
    private lateinit var tabSound: TextView
    private lateinit var soundPanel: View
    private lateinit var channelsBox: Spinner
    private lateinit var filterBox: Spinner
    private lateinit var eqOn: CheckBox
    private lateinit var eqCurve: EqCurve
    private val eqKnobs = mutableListOf<Knob>()
    private lateinit var headerTime: TextView
    private lateinit var list: ListView
    private lateinit var exportButton: Button

    private val handler = Handler(Looper.getMainLooper())
    // Lo que se toca vive en Playback, fuera de la ventana, para que siga sonando sin ella
    private var uri: Uri?
        get() = Playback.uri
        set(value) { Playback.uri = value }
    private var source: Source?
        get() = Playback.source
        set(value) { Playback.source = value }
    private var session: Session
        get() = Playback.session
        set(value) { Playback.session = value }
    private var loaded: Boolean
        get() = Playback.loaded
        set(value) { Playback.loaded = value }
    private var opening = false
    private var menu: Menu? = null
    private var skip = 5
    private var showingLoops = false
    private var showingSound = false
    private var tapped: Int? = null  // el tempo marcado (Marcar), hasta que se abre otro archivo
    private var xsc: Transcribe.File? = null  // un .xsc cuya grabación se está eligiendo
    private var chosen = -1  // la fila elegida de la tabla

    private fun chosenFolder() = Playback.chosenFolder(this)
    private fun stores() = Playback.stores(this)

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
            incoming != null && incoming != uri -> open(incoming)
            // Lo que ya suena (la ventana se cerró y vuelve): se muestra, sin abrirlo otra vez
            loaded && Engine.duration() > 0 -> attach()
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
        if (loaded) showPlaying()
    }

    override fun onPause() {
        super.onPause()
        Choreographer.getInstance().removeFrameCallback(ticker)
        save()
    }

    // Al salir de la ventana sigue sonando (el servicio de reproducción la reemplaza); al cerrarla del todo
    // sin que suene, el archivo se cierra
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !Engine.playing()) {
            save()
            Engine.nativeClose()
            loaded = false
            Playback.abandonFocus(this)
            PlaybackService.update(this)
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
        countButton = findViewById(R.id.count)
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
        tabSound = findViewById(R.id.tab_sound)
        soundPanel = findViewById(R.id.sound_panel)
        channelsBox = findViewById(R.id.channels)
        filterBox = findViewById(R.id.filter)
        eqOn = findViewById(R.id.eq_on)
        eqCurve = findViewById(R.id.eq_curve)
        headerTime = findViewById(R.id.header_time)
        list = findViewById(R.id.list)
        exportButton = findViewById(R.id.export)

        playButton.setOnClickListener { toggle() }
        findViewById<View>(R.id.stop).setOnClickListener {
            Engine.stop()
            showPlaying()
            PlaybackService.update(this)
        }
        // Al principio; con la cuenta previa, al primer pulso, si lo hay
        findViewById<View>(R.id.to_start).setOnClickListener {
            seek(Playback.firstBeat()?.takeIf { Playback.countIn(this).on } ?: 0.0)
        }
        countButton.setOnClickListener { setCountOn(!Playback.countIn(this).on) }
        countButton.setOnLongClickListener {
            countDialog()
            true
        }
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
            detents = SPEED_PRESETS  // (arrastrada, se detiene en las velocidades de siempre)
            onChange = { setSpeed(it / 100.0, fromKnob = true) }
            onMenu = { knobMenu(it, SPEED_PRESETS) { v -> setSpeed(v / 100.0, announce = true) } }
        }
        findViewById<View>(R.id.normal_speed).setOnClickListener { setSpeed(1.0, announce = true) }
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
            onMenu = { knobMenu(it, VOLUME_TICKS) { v -> volumeKnob.show(v); setVolume(v) } }
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
            onMenu = { knobMenu(it, symmetric(SEMITONE_CHOICES)) { v -> setPitch(v, centKnob.value) } }
        }
        centKnob.apply {
            minimum = -MAX_CENTS
            maximum = MAX_CENTS
            top = 0
            ticks = (minimum..maximum).filter { it % CENT_TICK == 0 }
            format = signed(" ¢")
            show(0)
            onChange = { setPitch(semitoneKnob.value, it) }
            onMenu = { knobMenu(it, symmetric(CENT_CHOICES)) { v -> setPitch(semitoneKnob.value, v) } }
        }
        originalPitch.setOnClickListener { setPitch(0, 0) }

        findViewById<View>(R.id.mark_a).setOnClickListener { selectionEdge(start = true) }
        findViewById<View>(R.id.mark_b).setOnClickListener { selectionEdge(start = false) }
        findViewById<View>(R.id.clear).setOnClickListener { setSelection(null) }

        // El reloj: un toque muestra el tiempo que falta, y luego (con un primer pulso) el compás y el pulso
        time.setOnClickListener { switchClock() }
        time.tooltipText = getString(R.string.clock_hint)

        // El panel de los marcadores, los bucles y el sonido
        tabBookmarks.setOnClickListener { showTab(loops = false) }
        tabLoops.setOnClickListener { showTab(loops = true) }
        tabSound.setOnClickListener { showSoundTab() }
        bindSound()
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

    // El panel Sonido: los canales y el filtro (siempre de acuerdo con el menú Oír) y las cinco bandas del ecualizador
    private fun bindSound() {
        fun spinner(box: Spinner, names: List<Int>, chosen: (Int) -> Unit) {
            box.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names.map { getString(it) }).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            box.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = chosen(position)
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        spinner(channelsBox, listOf(R.string.hear_both, R.string.hear_left, R.string.hear_right, R.string.hear_mono,
            R.string.hear_no_centre)) { i -> if (CHANNELS[i] != session.channels) setHearing(CHANNELS[i], session.filter) }
        spinner(filterBox, listOf(R.string.hear_none, R.string.hear_bass, R.string.hear_no_bass)) { i ->
            if (FILTERS[i] != session.filter) setHearing(session.channels, FILTERS[i])
        }
        val row = findViewById<LinearLayout>(R.id.eq_knobs)
        for ((i, band) in Equalizer.BANDS.withIndex()) {
            val name = if (band >= 1000) "${band / 1000} kHz" else "$band Hz"
            val knob = Knob(this).apply {
                minimum = -Equalizer.MAX_GAIN
                maximum = Equalizer.MAX_GAIN
                top = 0
                home = 0
                ticks = (minimum..maximum).filter { it % 3 == 0 }
                format = signed("")
                show(0)
                tooltipText = getString(R.string.eq_band_hint, name)
                onChange = { setBand(i, it) }
                onMenu = { knobMenu(it, symmetric(EQ_CHOICES)) { v -> setBand(i, v) } }
            }
            eqKnobs += knob
            row.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                addView(knob)
                addView(TextView(this@MainActivity).apply {
                    text = name
                    textSize = 11f
                    gravity = android.view.Gravity.CENTER
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        eqOn.tooltipText = getString(R.string.eq_on_hint)
        eqOn.setOnCheckedChangeListener { _, on ->
            if (on != session.eqOn) {
                session.eqOn = on
                applyEqualizer()
            }
        }
        findViewById<View>(R.id.eq_flat).apply {
            tooltipText = getString(R.string.eq_flat_hint)
            setOnClickListener { setEqualizer(Equalizer.FLAT) }
        }
        findViewById<View>(R.id.eq_presets).setOnClickListener { presetMenu(it) }
    }

    private fun setBand(band: Int, gain: Int) {
        setEqualizer(session.eq.toMutableList().also { it[band] = gain })
    }

    private fun setEqualizer(gains: List<Int>) {
        session.eq = Equalizer.clamp(gains)
        if (!Equalizer.isFlat(session.eq)) session.eqOn = true
        applyEqualizer()
    }

    private fun applyEqualizer() {
        Engine.setEqualizer((if (session.eqOn) session.eq else Equalizer.FLAT).toIntArray())
        showSound()
    }

    private fun showSound() {
        channelsBox.setSelection(CHANNELS.indexOf(session.channels).coerceAtLeast(0))
        filterBox.setSelection(FILTERS.indexOf(session.filter).coerceAtLeast(0))
        channelsBox.isEnabled = !loaded || Engine.channels() == 2
        for ((i, knob) in eqKnobs.withIndex()) knob.show(session.eq[i])
        eqOn.isChecked = session.eqOn
        eqCurve.gains = session.eq
        eqCurve.on = session.eqOn
        // La pestaña, del color del resaltado mientras el ecualizador cambia el sonido
        val active = session.eqOn && !Equalizer.isFlat(session.eq)
        tabSound.text = getString(R.string.sound) + if (active) " · EQ" else ""
        if (!showingSound) tabSound.setTextColor(if (active) palette.wave else palette.faint)
    }

    // Los ajustes del ecualizador: los de py-slowdown y los propios; guardar el de ahora, quitar uno propio
    private fun ownPresets() = Equalizer.readPresets(prefs.getString("eq_presets", null))

    private fun presetMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val own = ownPresets()
        val all = Equalizer.PRESETS.map { (key, gains) -> getString(PRESET_NAMES.getValue(key)) to gains } + own
        for ((i, preset) in all.withIndex()) {
            popup.menu.add(0, i, i, preset.first).apply {
                isCheckable = true
                isChecked = preset.second == session.eq
            }
        }
        popup.menu.add(1, SAVE_PRESET, all.size, R.string.eq_save_preset)
        if (own.isNotEmpty()) popup.menu.add(1, REMOVE_PRESET, all.size + 1, R.string.eq_remove_preset)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                SAVE_PRESET -> ask(getString(R.string.eq_preset_name), "") { name ->
                    if (name.isNotEmpty()) {
                        prefs.edit().putString("eq_presets", Equalizer.writePresets(Equalizer.withPreset(own, name, session.eq))).apply()
                    }
                }
                REMOVE_PRESET -> AlertDialog.Builder(this)
                    .setTitle(R.string.eq_remove_preset)
                    .setItems(own.map { it.first }.toTypedArray()) { _, which ->
                        prefs.edit().putString("eq_presets", Equalizer.writePresets(own.filterIndexed { i, _ -> i != which })).apply()
                    }
                    .show()
                else -> all.getOrNull(item.itemId)?.let { setEqualizer(it.second) }
            }
            true
        }
        popup.show()
    }

    // El menú de una perilla (mantener el dedo en ella): sus valores de costumbre, el de ahora marcado, y volver al
    // de siempre
    private fun knobMenu(knob: Knob, values: List<Int>, choose: (Int) -> Unit) {
        val popup = PopupMenu(this, knob)
        val shown = values.filter { it in knob.minimum..knob.maximum }
        for ((i, v) in shown.withIndex()) {
            popup.menu.add(0, i, i, knob.format(v)).apply {
                isCheckable = true
                isChecked = v == knob.value
            }
        }
        popup.menu.add(1, shown.size, shown.size, getString(R.string.back_to, knob.format(knob.home)))
        popup.setOnMenuItemClickListener { item ->
            choose(shown.getOrNull(item.itemId) ?: knob.home)
            true
        }
        popup.show()
    }

    private fun symmetric(steps: List<Int>) = (steps + steps.map { -it } + 0).distinct().sorted()

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
            EXPORT_AUDIO -> exportAudioTo(target)
            IMPORT_SLX -> readSlx(target)
            IMPORT_XSC -> readXsc(target)
            PICK_FOR_XSC -> {
                try {
                    contentResolver.takePersistableUriPermission(target, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                }
                xsc?.let { open(target, it.session) }
                xsc = null
            }
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

    // OVERRIDE: lo que se toma en lugar del .slx (de un .xsc de Transcribe!), y se guarda luego en él
    private fun open(target: Uri, override: Session? = null) {
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
            val kept = override ?: stores().firstNotNullOfOrNull { folder ->
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
                tapped = null
                summarized = false
                remember(Recent(target.toString(), described.name, FloatArray(Recents.COLUMNS)))
                loaded(kept ?: Session())
                if (override != null) {
                    save()
                    Toast.makeText(this, getString(R.string.xsc_imported, override.bookmarks.size, override.loops.size),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // La ventana vuelve a lo que suena: lo de la sesión, sin tocar el motor
    private fun attach() {
        title = source?.name ?: getString(R.string.app_name)
        hint.visibility = View.GONE
        waveform.rate = Engine.rate()
        waveform.duration = 0.0
        waveform.duration = Engine.duration()
        volumeKnob.show(session.volume ?: NORMAL_VOLUME)
        waveform.selection = session.selection
        showSelection()
        showRepeat()
        waveform.update(Engine.position())
        chosen = -1
        showSpeed()
        showPitch()
        showMarks()
        showCount()
        showSound()
        showPlaying()
        updateMenu()
    }

    private fun loaded(kept: Session) {
        loaded = true
        session = kept
        hint.visibility = View.GONE
        waveform.rate = Engine.rate()
        waveform.duration = 0.0
        waveform.duration = Engine.duration()
        Engine.setFormants(session.formants)
        applyHearing()
        session.eq = Equalizer.clamp(session.eq)
        applyEqualizer()
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
        showCount()
        updateMenu()
        PlaybackService.update(this)  // (la notificación, si la hay, muestra el archivo nuevo)
    }

    private fun showEmpty() {
        loaded = false
        title = getString(R.string.app_name)
        hint.text = getString(R.string.open_hint)
        waveform.duration = 0.0
        waveform.bookmarks = emptyList()
        waveform.loops = emptyList()
        waveform.selection = null
        // La pantalla de inicio: las grabaciones de hace poco (gris la que ya no está); sin ellas, la explicación
        waveform.recentsTitle = getString(R.string.recent_recordings)
        waveform.recents = recents().map { it to reachable(Uri.parse(it.uri)) }
        hint.visibility = if (waveform.recents.isEmpty()) View.VISIBLE else View.GONE
        showClock("0:00.0 / 0:00.0")
        status.text = ""
        session = Session()
        chosen = -1
        showSpeed()
        showPitch()
        showMarks()
        showSelection()
        Engine.setEqualizer(Equalizer.FLAT.toIntArray())
        showSound()
        updateMenu()
    }

    // --- las grabaciones de hace poco: la pantalla de inicio

    private var summarized = false  // si la de ahora ya tiene su figura

    private fun recents() = Recents.read(prefs.getString("recents", null))

    private fun remember(recent: Recent) {
        prefs.edit().putString("recents", Recents.write(Recents.opened(recents(), recent))).apply()
    }

    // La figura de la onda de lo abierto, cuando está leído hasta su final
    private fun summarize() {
        if (summarized || !loaded || !Engine.done()) return
        summarized = true
        val target = uri ?: return
        val name = source?.name ?: return
        val peaks = FloatArray(2 * Recents.COLUMNS)
        Engine.peaks(0.0, Engine.duration() * Engine.rate() / Recents.COLUMNS, peaks)
        remember(Recent(target.toString(), name, Recents.summary(peaks)))
    }

    private fun reachable(target: Uri): Boolean {
        if (!hasPermission(target)) return false
        return try {
            contentResolver.openAssetFileDescriptor(target, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    override fun onRecent(recent: Recent) {
        open(Uri.parse(recent.uri))
    }

    private fun closeFile() {
        save()
        Engine.pause()
        Engine.nativeClose()
        loaded = false
        PlaybackService.update(this)
        uri = null
        source = null
        prefs.edit().remove("last").apply()
        showEmpty()
    }

    private fun save(announce: Boolean = false) {
        // En la carpeta elegida, si la hay, y siempre en la de la aplicación
        val (shown, errors) = Playback.save(this)
        for (error in errors) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        if (announce && shown != null) Toast.makeText(this, getString(R.string.saved, shown), Toast.LENGTH_SHORT).show()
    }

    // --- tocar

    private fun toggle() {
        if (!loaded) {
            pick()
            return
        }
        Playback.toggle(this)
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

    // ANNOUNCE: el valor, grande un momento sobre la onda (cambiado con el teclado o un menú, no con la perilla)
    private fun setSpeed(speed: Double, fromKnob: Boolean = false, announce: Boolean = false) {
        session.speed = Engine.clampSpeed(Math.round(speed * 100) / 100.0)
        Engine.setSpeed(session.speed)
        if (!fromKnob) showSpeed()
        if (announce && loaded) waveform.flash("${(session.speed * 100).roundToInt()}%")
    }

    private fun announcePitch() {
        if (!loaded) return
        val st = semitoneKnob.format(semitoneKnob.value).trim()
        waveform.flash(if (centKnob.value != 0) "$st ${centKnob.format(centKnob.value)}" else st)
    }

    // Deshacer los cambios del sonido: velocidad 100 %, la altura de la grabación, sin formantes, los dos canales sin
    // filtro y el ecualizador plano, de una vez; el volumen se queda
    private fun resetSound() {
        setSpeed(1.0)
        setPitch(0, 0)
        session.formants = false
        Engine.setFormants(false)
        session.eq = Equalizer.FLAT
        session.eqOn = true
        setHearing("both", "none")
        applyEqualizer()
        updateMenu()
        if (loaded) waveform.flash(getString(R.string.sound_reset))
    }

    private fun selectAll() {
        if (loaded && Engine.duration() > 0) setSelection(0.0 to Engine.duration())
    }

    private fun zoomToSelection() {
        session.selection?.let { (a, b) -> waveform.zoomTo(a, b) }
    }

    // --- el reloj: lo pasado, lo que falta, o (con un primer pulso) el compás y el pulso

    private fun clockText(position: Double, length: Double): String {
        val mode = prefs.getString("clock", "elapsed")
        val first = Playback.firstBeat()
        if (mode == "bars" && first != null) {
            val count = Playback.countIn(this)
            val beats = prefs.getInt("countin_beats", 4).coerceIn(1, CountInRules.MAX_BEATS)
            val where = CountInRules.barAndBeat(position, first, count.bpm.toDouble(), beats)
            return if (where == null) getString(R.string.before_first_beat) else getString(R.string.bar_beat, where.first, where.second)
        }
        if (mode == "remaining") return "−${TimeFormat.long(maxOf(0.0, length - position))} / ${TimeFormat.long(length)}"
        return "${TimeFormat.long(position)} / ${TimeFormat.long(length)}"
    }

    private fun switchClock() {
        val modes = if (Playback.firstBeat() != null) listOf("elapsed", "remaining", "bars") else listOf("elapsed", "remaining")
        val now = prefs.getString("clock", "elapsed")
        val next = modes.getOrNull(modes.indexOf(now) + 1) ?: modes[0]
        prefs.edit().putString("clock", next).apply()
        if (loaded) showClock(clockText(Engine.position(), Engine.duration()))
    }

    // (el compás y el pulso, más largos que el tiempo, en una letra menor: la fila no se sale de la pantalla)
    private fun showClock(text: String) {
        if (time.text.toString() == text) return
        time.text = text
        time.textSize = if (text.length > 20) 14f else 18f
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
            setSpeed(SPEED_PRESETS[item.itemId] / 100.0, announce = true)
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
                showClock(clockText(here, Engine.duration()))
                val done = Engine.done()
                if (!done || decoding) {
                    decoding = !done
                    waveform.duration = Engine.duration()
                    val total = Engine.duration() * Engine.rate()
                    status.text = if (done) "" else "${(100 * Engine.decoded() / maxOf(1.0, total)).roundToInt()} %"
                    if (done) applyRepeat()  // (el archivo entero se repite cuando está leído hasta su final)
                }
                if (done && !summarized) summarize()
                // El pulso de la cuenta previa, grande sobre la onda (antes del primer golpe, nada)
                val counting = Engine.counting()
                waveform.count = if (counting >= 100) counting / 100 else null
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
        updateMenu()
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
        showRepeat()
    }

    // --- la exportación del audio: como suena, en WAV o M4A

    private data class ExportChoice(val range: Pair<Double, Double>?, val m4a: Boolean, val heard: Boolean)
    private var exportChoice: ExportChoice? = null

    private fun exportAudioDialog() {
        if (!loaded) return
        if (!Engine.done()) {
            Toast.makeText(this, R.string.still_reading, Toast.LENGTH_SHORT).show()
            return
        }
        val selection = session.selection
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        fun label(text: Int) = TextView(this).apply { setText(text); setPadding(0, dp(8), 0, 0) }
        val what = android.widget.RadioGroup(this)
        val whole = android.widget.RadioButton(this).apply { id = View.generateViewId(); setText(R.string.export_whole) }
        val part = android.widget.RadioButton(this).apply {
            id = View.generateViewId()
            text = selection?.let { getString(R.string.export_selection, TimeFormat.long(it.first), TimeFormat.long(it.second)) } ?: ""
            isEnabled = selection != null
        }
        what.addView(whole)
        what.addView(part)
        what.check(if (selection != null) part.id else whole.id)
        val format = android.widget.RadioGroup(this)
        val wav = android.widget.RadioButton(this).apply { id = View.generateViewId(); setText(R.string.format_wav) }
        val m4a = android.widget.RadioButton(this).apply { id = View.generateViewId(); setText(R.string.format_m4a) }
        format.addView(wav)
        format.addView(m4a)
        format.check(if (prefs.getBoolean("export_m4a", false)) m4a.id else wav.id)
        val heard = CheckBox(this).apply { setText(R.string.export_heard); isChecked = prefs.getBoolean("export_heard", true) }
        val st = semitoneKnob.format(semitoneKnob.value) + if (centKnob.value != 0) " " + centKnob.format(centKnob.value) else ""
        val note = TextView(this).apply {
            text = getString(R.string.export_note, number(session.speed), st)
            setPadding(0, dp(8), 0, 0)
        }
        box.addView(label(R.string.export_what))
        box.addView(what)
        box.addView(label(R.string.export_format))
        box.addView(format)
        box.addView(heard)
        box.addView(note)
        AlertDialog.Builder(this)
            .setTitle(R.string.export_audio_title)
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.export) { _, _ ->
                val choice = ExportChoice(if (what.checkedRadioButtonId == part.id) selection else null,
                    format.checkedRadioButtonId == m4a.id, heard.isChecked)
                prefs.edit().putBoolean("export_m4a", choice.m4a).putBoolean("export_heard", choice.heard).apply()
                exportChoice = choice
                val stem = source?.name?.substringBeforeLast('.') ?: "audio"
                val speed = if (session.speed != 1.0) " (${Math.round(session.speed * 100)}%)" else ""
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = if (choice.m4a) "audio/mp4" else "audio/wav"
                    putExtra(Intent.EXTRA_TITLE, "$stem$speed.${if (choice.m4a) "m4a" else "wav"}")
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, EXPORT_AUDIO)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun exportAudioTo(target: Uri) {
        val choice = exportChoice ?: return
        val name = describe(target).name
        val pfd = try {
            contentResolver.openFileDescriptor(target, "rw")
        } catch (e: Exception) {
            null
        }
        if (pfd == null) {
            Toast.makeText(this, getString(R.string.cannot_export, name, "?"), Toast.LENGTH_LONG).show()
            return
        }
        val channels = if (choice.heard) CHANNELS.indexOf(session.channels).coerceAtLeast(0) else 0
        val filter = if (choice.heard) FILTERS.indexOf(session.filter).coerceAtLeast(0) else 0
        val eq = if (choice.heard && session.eqOn) session.eq else Equalizer.FLAT
        val volume = volumeKnob.value
        val gain = if (choice.heard) (volume / 100.0) * (volume / 100.0) * (if (boost.isChecked) boostFactor() else 1.0) else 1.0
        val (start, end) = choice.range ?: (0.0 to -1.0)
        val failed = Engine.exportStart(pfd.fd, if (choice.m4a) 1 else 0, start, end, session.speed, session.pitch,
            session.formants, prefs.getInt("quality", Engine.QUALITY_HIGH), channels, filter, eq.toIntArray(), gain)
        pfd.close()  // (el motor tiene su propia copia)
        if (failed != null) {
            Toast.makeText(this, getString(R.string.cannot_export, name, failed), Toast.LENGTH_LONG).show()
            return
        }
        val bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.exporting, name))
            .setView(bar)
            .setCancelable(false)
            .setNegativeButton(R.string.cancel) { _, _ -> Engine.exportCancel() }
            .show()
        val poll = object : Runnable {
            override fun run() {
                bar.progress = (Engine.exportProgress() * 1000).toInt()
                if (!Engine.exportDone()) {
                    handler.postDelayed(this, 200)
                    return
                }
                dialog.dismiss()
                val error = Engine.exportError()
                Engine.exportRelease()
                if (error == null) {
                    Toast.makeText(this@MainActivity, getString(R.string.exported, name), Toast.LENGTH_SHORT).show()
                } else {
                    // (lo que quedó a medias se borra)
                    try { android.provider.DocumentsContract.deleteDocument(contentResolver, target) } catch (_: Exception) { }
                    if (error != "cancelled") Toast.makeText(this@MainActivity, getString(R.string.cannot_export, name, error), Toast.LENGTH_LONG).show()
                }
            }
        }
        handler.post(poll)
    }

    // --- cómo se oye: los nombres son los del .slx de py-slowdown

    private fun applyHearing() {
        Engine.setHearing(CHANNELS.indexOf(session.channels).coerceAtLeast(0), FILTERS.indexOf(session.filter).coerceAtLeast(0))
    }

    // Desde el menú Oír o desde el panel Sonido: los dos dicen siempre lo mismo
    private fun setHearing(channels: String, filter: String) {
        session.channels = channels
        session.filter = filter
        applyHearing()
        updateMenu()
        showSound()
    }

    // --- la cuenta previa

    private fun setCountOn(on: Boolean) {
        Playback.setCountIn(this, Playback.countIn(this).copy(on = on))
        showCount()
    }

    private fun showCount() {
        val on = Playback.countIn(this).on
        countButton.imageTintList = if (on) ColorStateList.valueOf(palette.wave) else null
        countButton.setBackgroundColor(if (on) (palette.wave and 0x00ffffff) or 0x33000000 else Color.TRANSPARENT)
    }

    // Un golpe con el pulso de la música: el tempo de la grabación (si sigue la velocidad, lo oído a esa velocidad)
    private fun tap(): Int? {
        val heard = tapTempo.tap(System.nanoTime() / 1e9)
        if (heard == null) {
            Toast.makeText(this, R.string.tap_again, Toast.LENGTH_SHORT).show()
            return null
        }
        val follow = prefs.getBoolean("countin_follow", true)
        val bpm = CountInRules.recordingTempo(heard, session.speed, follow)
        Playback.setCountIn(this, Playback.countIn(this).copy(bpm = bpm))
        tapped = Math.round(heard).toInt()
        Toast.makeText(this, getString(R.string.tempo_taps, bpm, tapTempo.taps), Toast.LENGTH_SHORT).show()
        return bpm
    }

    // El primer pulso de la grabación aquí: un marcador propio, uno solo por archivo
    private fun firstBeatHere() {
        if (!loaded) return
        val here = Math.round(Engine.position() * 1000) / 1000.0
        session.bookmarks.forEach { it.firstBeat = false }
        val near = session.bookmarks.firstOrNull { kotlin.math.abs(it.time - here) < 0.05 }
        if (near != null) near.firstBeat = true else session.bookmarks.add(Bookmark(here, "", true))
        changed()
    }

    private fun countDialog() {
        val count = Playback.countIn(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        fun row(label: Int, view: View): View = LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply { setText(label) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(view)
        }
        val on = android.widget.Switch(this).apply { setText(R.string.count_toggle); isChecked = count.on }
        val beats = android.widget.NumberPicker(this).apply {
            minValue = 1
            maxValue = CountInRules.MAX_BEATS
            value = prefs.getInt("countin_beats", 4).coerceIn(1, CountInRules.MAX_BEATS)
        }
        val bpm = android.widget.NumberPicker(this).apply {
            minValue = CountInRules.MIN_BPM
            maxValue = CountInRules.MAX_BPM
            value = count.bpm.coerceIn(CountInRules.MIN_BPM, CountInRules.MAX_BPM)
            wrapSelectorWheel = false
        }
        // Marcar: muestra el tempo oído mientras se marca («…» tras el primer golpe), y lo guarda hasta otro archivo
        val tapButton = Button(this).apply {
            text = tapped?.let { getString(R.string.tap_bpm, it) } ?: getString(R.string.tap)
            minEms = 5
            tooltipText = getString(R.string.tap_hint)
            setOnClickListener {
                val got = tap()
                if (got != null) bpm.value = got
                text = tapped?.takeIf { got != null }?.let { getString(R.string.tap_bpm, it) } ?: "…"
            }
        }
        val follow = CheckBox(this).apply {
            setText(R.string.follow_speed)
            isChecked = prefs.getBoolean("countin_follow", true)
        }
        val offset = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(String.format(java.util.Locale.ROOT, "%.2f", count.offset))
            minEms = 4
        }
        val summary = TextView(this).apply { setPadding(0, dp(8), 0, 0) }
        fun explain() {
            val tempo = CountInRules.tempo(bpm.value, session.speed, follow.isChecked)
            var text = getString(R.string.count_summary, number(session.speed), beats.value, Math.round(tempo).toInt())
            Playback.firstBeat()?.let { text += " " + getString(R.string.first_beat_grid, TimeFormat.long(it)) }
            summary.text = text
            offset.isEnabled = Playback.firstBeat() == null  // (con un primer pulso, el desfase lo da él)
        }
        beats.setOnValueChangedListener { _, _, _ -> explain() }
        bpm.setOnValueChangedListener { _, _, _ -> explain() }
        follow.setOnCheckedChangeListener { _, _ -> explain() }
        box.addView(on)
        box.addView(row(R.string.clicks, beats))
        box.addView(row(R.string.tempo_bpm, LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(bpm)
            addView(tapButton)
        }))
        box.addView(follow)
        box.addView(row(R.string.recording_offset, offset))
        box.addView(summary)
        explain()
        AlertDialog.Builder(this)
            .setTitle(R.string.count_in)
            .setView(android.widget.ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.ok) { _, _ ->
                val seconds = offset.text.toString().replace(',', '.').toDoubleOrNull() ?: count.offset
                prefs.edit().putInt("countin_beats", beats.value).putBoolean("countin_follow", follow.isChecked).apply()
                Playback.setCountIn(this, CountIn(on.isChecked, bpm.value,
                    seconds.coerceIn(-CountInRules.MAX_OFFSET, CountInRules.MAX_OFFSET)))
                showCount()
                save()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRepeat() {
        waveform.repeating = session.repeat && session.selection != null
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

    // Un bucle tocado al pie de la onda: se elige, se repite y se va a su principio
    override fun onLoop(loop: Loop) {
        setSelection(loop.start to loop.end)
        setRepeat(true)
        seek(loop.start)
        if (showingLoops) {
            chosen = sortedLoops().indexOf(loop)
            table.notifyDataSetChanged()
        }
    }

    // --- el panel de los marcadores y los bucles

    private fun showTab(loops: Boolean, sound: Boolean = false) {
        showingLoops = loops && !sound
        showingSound = sound
        chosen = -1
        for ((tab, on) in listOf(tabBookmarks to (!loops && !sound), tabLoops to showingLoops, tabSound to sound)) {
            tab.setTextColor(if (on) palette.text else palette.faint)
            tab.setBackgroundColor(if (on) palette.base else Color.TRANSPARENT)
            tab.paint.isFakeBoldText = on
            tab.invalidate()
        }
        // El sonido, en lugar de la tabla y sus botones
        findViewById<View>(R.id.table).visibility = if (sound) View.GONE else View.VISIBLE
        findViewById<View>(R.id.panel_buttons).visibility = if (sound) View.GONE else View.VISIBLE
        soundPanel.visibility = if (sound) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.panel_title).setText(if (sound) R.string.sound else R.string.bookmarks_and_loops)
        headerTime.text = getString(R.string.time)
        exportButton.isEnabled = !loops
        table.notifyDataSetChanged()
        if (::eqCurve.isInitialized) showSound()
    }

    private fun showSoundTab() = showTab(loops = false, sound = true)

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
        if (showingLoops || showingSound) showTab(loops = false)
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
        waveform.loops = session.loops.toList()
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
        for (id in listOf(R.id.save, R.id.close, R.id.export_slx, R.id.import_slx, R.id.export_audio, R.id.select_all)) {
            menu.findItem(id).isEnabled = loaded
        }
        menu.findItem(R.id.zoom_selection).isEnabled = loaded && session.selection != null
        // Cómo se oye: los canales (no con un archivo mono) y el filtro
        menu.findItem(HEAR_CHANNELS.entries.firstOrNull { it.value == session.channels }?.key ?: R.id.hear_both).isChecked = true
        menu.findItem(HEAR_FILTERS.entries.firstOrNull { it.value == session.filter }?.key ?: R.id.hear_none).isChecked = true
        menu.setGroupEnabled(R.id.hear_channels, !loaded || Engine.channels() == 2)
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
            in HEAR_CHANNELS.keys -> setHearing(HEAR_CHANNELS.getValue(item.itemId), session.filter)
            in HEAR_FILTERS.keys -> setHearing(session.channels, HEAR_FILTERS.getValue(item.itemId))
            R.id.hear_equalizer -> showSoundTab()
            R.id.reset_sound -> resetSound()
            R.id.select_all -> selectAll()
            R.id.zoom_selection -> zoomToSelection()
            R.id.import_xsc -> importXsc()
            R.id.licenses -> showLicenses()
            R.id.export_audio -> exportAudioDialog()
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

    // --- los archivos de Transcribe! (.xsc): su grabación, con sus marcadores, bucles, velocidad y afinación

    private fun importXsc() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, IMPORT_XSC)
    }

    private fun readXsc(target: Uri) {
        val name = describe(target).name
        val text = try {
            contentResolver.openInputStream(target)?.use { stream ->
                val bytes = stream.readBytes()
                // (en UTF-8, o si no lo es, en Latin-1, como lo lee py-slowdown)
                val decoder = Charsets.UTF_8.newDecoder()
                try { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() } catch (_: java.nio.charset.CharacterCodingException) {
                    String(bytes, Charsets.ISO_8859_1)
                }
            }
        } catch (_: Exception) {
            null
        }
        val file = text?.let { Transcribe.read(it, getString(R.string.first_beat_label)) }
        if (file == null) {
            Toast.makeText(this, getString(R.string.not_xsc, name), Toast.LENGTH_LONG).show()
            return
        }
        // La grabación: la abierta, si se llama como dice el .xsc; si no, la que se elija
        val current = uri
        if (current != null && loaded && file.recording.isNotEmpty() && source?.name == file.recording) {
            open(current, file.session)
            return
        }
        xsc = file
        Toast.makeText(this, getString(R.string.xsc_choose_recording, file.recording.ifEmpty { "?" }), Toast.LENGTH_LONG).show()
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*", "application/ogg"))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, PICK_FOR_XSC)
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
            // (lo que no es un aviso de derechos, en el idioma de la interfaz)
            val note = when (c.name) {
                "minimp3" -> " · " + getString(R.string.lic_public_domain)
                "Material Icons" -> " (" + getString(R.string.lic_buttons) + ")"
                else -> ""
            }
            val holder = c.holder + if (c.name == "minimp3") note else ""
            val name = title + if (c.name == "Material Icons") note else ""
            "$name\n${c.license} · $holder"
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
            KeyEvent.KEYCODE_DPAD_UP -> if (ctrl) { setPitch(semitoneKnob.value + 1, centKnob.value); announcePitch() }
                else setSpeed(session.speed + SPEED_STEP, announce = true)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (ctrl) { setPitch(semitoneKnob.value - 1, centKnob.value); announcePitch() }
                else setSpeed(session.speed - SPEED_STEP, announce = true)
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS -> setSpeed(session.speed + SPEED_STEP, announce = true)
            KeyEvent.KEYCODE_MINUS -> setSpeed(session.speed - SPEED_STEP, announce = true)
            KeyEvent.KEYCODE_N -> when {
                ctrl && shift -> resetSound()
                shift -> { setPitch(0, 0); announcePitch() }
                else -> setSpeed(1.0, announce = true)
            }
            KeyEvent.KEYCODE_A -> if (ctrl) selectAll() else return super.onKeyDown(keyCode, event)
            KeyEvent.KEYCODE_Z -> zoomToSelection()
            KeyEvent.KEYCODE_S -> { Engine.stop(); showPlaying(); PlaybackService.update(this) }
            KeyEvent.KEYCODE_B -> addBookmark(Engine.position())
            KeyEvent.KEYCODE_I -> selectionEdge(start = true)
            KeyEvent.KEYCODE_O -> selectionEdge(start = false)
            KeyEvent.KEYCODE_L -> if (shift) saveLoop() else setRepeat(!session.repeat)
            KeyEvent.KEYCODE_C -> if (shift) firstBeatHere() else setCountOn(!Playback.countIn(this).on)
            KeyEvent.KEYCODE_T -> tap()
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

    companion object {
        private const val PICK = 1
        private const val EXPORT = 2
        private const val EXPORT_SLX = 3
        private const val IMPORT_SLX = 4
        private const val TREE = 5
        private const val EXPORT_AUDIO = 6
        private const val IMPORT_XSC = 7
        private const val PICK_FOR_XSC = 8
        private const val SAVE_PRESET = 1000
        private const val REMOVE_PRESET = 1001
        val CHANNELS = listOf("both", "left", "right", "mono", "no_centre")  // los nombres del .slx de py-slowdown
        val FILTERS = listOf("none", "bass", "no_bass")
        val SEMITONE_CHOICES = listOf(1, 2, 3, 5, 7, 12)  // los intervalos de costumbre, en el menú de la perilla
        val CENT_CHOICES = listOf(5, 10, 25, 50)
        val EQ_CHOICES = listOf(3, 6, 9, 12)
        private val PRESET_NAMES = mapOf("eq_flat" to R.string.eq_flat, "eq_bass_up" to R.string.eq_bass_up,
            "eq_bass_line" to R.string.eq_bass_line, "eq_voice" to R.string.eq_voice, "eq_bright" to R.string.eq_bright,
            "eq_warm" to R.string.eq_warm, "eq_hiss" to R.string.eq_hiss)
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
        private val HEAR_CHANNELS = mapOf(R.id.hear_both to "both", R.id.hear_left to "left",
            R.id.hear_right to "right", R.id.hear_mono to "mono", R.id.hear_no_centre to "no_centre")
        private val HEAR_FILTERS = mapOf(R.id.hear_none to "none", R.id.hear_bass to "bass", R.id.hear_no_bass to "no_bass")
        val SPEED_PRESETS = listOf(50, 60, 70, 75, 80, 90, 100, 110, 125, 150)  // las de py-slowdown

        /** The bookmarks as plain text, one per line: time, a tab, the label (as py-slowdown exports them). */
        fun exportText(bookmarks: List<Bookmark>, audioName: String): String =
            (listOf("# $audioName") + bookmarks.sortedBy { it.time }.map { "${TimeFormat.long(it.time)}\t${it.label}" })
                .joinToString("\n") + "\n"
    }
}
