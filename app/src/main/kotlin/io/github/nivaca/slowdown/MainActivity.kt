// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * La ventana de Slowdown: abre un archivo, lo muestra como onda y lo toca a la
 * velocidad y la altura elegidas, con marcadores, una selección que se repite
 * y bucles guardados.
 *
 * Lo que se hace con cada archivo (la posición, la velocidad, la altura, la
 * selección, los marcadores y los bucles) se guarda en su .slx (Sidecar.kt) al
 * salir de la ventana, al cerrar el archivo y al cambiar un marcador o un bucle.
 * El último archivo se abre otra vez al volver.
 */
package io.github.nivaca.slowdown

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
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
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : Activity(), WaveformView.Listener {

    private lateinit var prefs: SharedPreferences
    private lateinit var waveform: WaveformView
    private lateinit var hint: TextView
    private lateinit var time: TextView
    private lateinit var status: TextView
    private lateinit var playButton: ImageButton
    private lateinit var speedBar: SeekBar
    private lateinit var speedLabel: TextView
    private lateinit var pitchLabel: TextView
    private lateinit var centsBar: SeekBar
    private lateinit var selectionLabel: TextView
    private lateinit var repeatButton: ImageButton
    private lateinit var bookmarkList: LinearLayout
    private lateinit var loopList: LinearLayout
    private lateinit var loopsHeading: TextView
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton

    private val handler = Handler(Looper.getMainLooper())
    private var uri: Uri? = null
    private var source: Source? = null
    private var session = Session()
    private var loaded = false
    private var opening = false
    private var menu: Menu? = null
    private var skip = 5

    // La carpeta de los .slx, dentro de la aplicación
    private val slxFolder by lazy { File(filesDir, "slx") }

    private val version by lazy { packageManager.getPackageInfo(packageName, 0).versionName ?: "" }

    // --- el ciclo de la ventana

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.main)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        skip = prefs.getInt("skip", 5)
        setActionBar(findViewById(R.id.toolbar))
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
        speedBar = findViewById(R.id.speed)
        speedLabel = findViewById(R.id.speed_label)
        pitchLabel = findViewById(R.id.pitch_label)
        centsBar = findViewById(R.id.cents)
        selectionLabel = findViewById(R.id.selection)
        repeatButton = findViewById(R.id.repeat)
        bookmarkList = findViewById(R.id.bookmark_list)
        loopList = findViewById(R.id.loop_list)
        loopsHeading = findViewById(R.id.loops_heading)
        backButton = findViewById(R.id.back)
        forwardButton = findViewById(R.id.forward)

        playButton.setOnClickListener { toggle() }
        findViewById<View>(R.id.to_start).setOnClickListener { seek(0.0) }
        findViewById<View>(R.id.to_end).setOnClickListener { seek(Engine.duration()) }
        repeating(backButton) { jump(-skip.toDouble()) }
        repeating(forwardButton) { jump(skip.toDouble()) }
        updateSkipDescriptions()

        speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) setSpeed(Engine.MIN_SPEED + progress / 100.0)
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })
        findViewById<View>(R.id.slower).setOnClickListener { setSpeed(session.speed - 0.05) }
        findViewById<View>(R.id.faster).setOnClickListener { setSpeed(session.speed + 0.05) }
        speedLabel.setOnClickListener { setSpeed(1.0) }
        findViewById<View>(R.id.speed_50).setOnClickListener { setSpeed(0.5) }
        findViewById<View>(R.id.speed_75).setOnClickListener { setSpeed(0.75) }
        findViewById<View>(R.id.speed_100).setOnClickListener { setSpeed(1.0) }

        findViewById<View>(R.id.lower).setOnClickListener { setPitch(semitones() - 1, cents()) }
        findViewById<View>(R.id.higher).setOnClickListener { setPitch(semitones() + 1, cents()) }
        pitchLabel.setOnClickListener { setPitch(0, 0) }
        centsBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) setPitch(semitones(), progress)
            }
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
        })

        findViewById<View>(R.id.mark_a).setOnClickListener { selectionEdge(start = true) }
        findViewById<View>(R.id.mark_b).setOnClickListener { selectionEdge(start = false) }
        repeatButton.setOnClickListener { setRepeat(!session.repeat) }
        findViewById<View>(R.id.clear).setOnClickListener { setSelection(null) }
        findViewById<View>(R.id.save_loop).setOnClickListener { saveLoop() }
        findViewById<View>(R.id.add_bookmark).setOnClickListener { if (loaded) addBookmark(Engine.position()) }
        for (id in listOf(R.id.to_start, R.id.back, R.id.forward, R.id.to_end, R.id.slower, R.id.faster,
            R.id.lower, R.id.higher, R.id.repeat, R.id.save_loop, R.id.clear, R.id.add_bookmark)) {
            val view = findViewById<View>(id)
            view.tooltipText = view.contentDescription
        }
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

    private fun semitones(): Int = session.pitch.let { Math.round(it - cents() / 100.0).toInt() }
    private fun cents(): Int = centsBar.progress

    // --- abrir y cerrar

    private fun hasPermission(uri: Uri) =
        uri.scheme == "file" || contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    private val picker = 1

    private fun pick() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*", "application/ogg"))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, picker)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(request, result, data)
        if (request == picker && result == RESULT_OK) {
            val chosen = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(chosen, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // (algunos proveedores no lo permiten: se puede tocar igual, pero no reabrir)
            }
            open(chosen)
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
            val kept = try { Sidecar.load(described, slxFolder) } catch (_: Exception) { null }
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
        Engine.setSpeed(session.speed)
        Engine.setPitch(session.pitch)
        Engine.setVolume((session.volume ?: 100) / 100.0)
        session.speed = Engine.clampSpeed(session.speed)
        val cents = Math.round((session.pitch - Math.round(session.pitch)) * 100).toInt()
        centsBar.progress = cents
        setSelection(session.selection, save = false)
        if (session.position > 0) Engine.seek(session.position)
        waveform.update(session.position)
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
        time.text = ""
        status.text = ""
        session = Session()
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
        try {
            val file = Sidecar.save(session, source, slxFolder, version)
            if (announce) Toast.makeText(this, getString(R.string.saved, file.name), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: e.toString(), Toast.LENGTH_LONG).show()
        }
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

    private fun setSpeed(speed: Double) {
        session.speed = Engine.clampSpeed(Math.round(speed * 100) / 100.0)
        Engine.setSpeed(session.speed)
        showSpeed()
    }

    private fun showSpeed() {
        speedLabel.text = getString(R.string.speed_value, String.format(Locale.ROOT, "%.2f", session.speed))
        speedBar.progress = ((session.speed - Engine.MIN_SPEED) * 100).roundToInt()
    }

    private fun setPitch(semitones: Int, cents: Int) {
        val whole = semitones.coerceIn(-Engine.MAX_PITCH.toInt(), Engine.MAX_PITCH.toInt())
        session.pitch = (whole + cents / 100.0).coerceIn(-Engine.MAX_PITCH, Engine.MAX_PITCH)
        centsBar.progress = cents
        Engine.setPitch(session.pitch)
        showPitch()
    }

    private fun showPitch() {
        val st = semitones()
        val c = cents()
        pitchLabel.text = getString(R.string.pitch_value, if (st > 0) "+$st" else "$st", if (c > 0) "+$c" else "$c")
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
                }
                if (Engine.takeEnded() || wasPlaying != Engine.playing()) {
                    wasPlaying = Engine.playing()
                    showPlaying()
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    // --- la selección

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
        if (selection == null) session.repeat = false
        waveform.selection = selection
        applyRepeat()
        showSelection()
        if (save) save()
    }

    private fun setRepeat(on: Boolean) {
        if (on && session.selection == null) {
            Toast.makeText(this, R.string.no_selection, Toast.LENGTH_SHORT).show()
            return
        }
        session.repeat = on
        applyRepeat()
        showSelection()
    }

    private fun applyRepeat() {
        val selection = session.selection
        if (loaded) {
            if (session.repeat && selection != null) Engine.setLoop(selection.first, selection.second)
            else if (Engine.looping()) Engine.setLoop(-1.0, -1.0)
        }
        waveform.repeating = session.repeat
        repeatButton.isActivated = session.repeat
        repeatButton.imageTintList = if (session.repeat)
            android.content.res.ColorStateList.valueOf(getColor(android.R.color.system_accent1_300)) else null
    }

    private fun showSelection() {
        val selection = session.selection
        selectionLabel.text = if (selection == null) getString(R.string.no_selection)
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

    // --- los marcadores y los bucles

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

    private fun addBookmark(seconds: Double) {
        if (!loaded) return
        val mark = Bookmark(Math.round(seconds * 1000) / 1000.0)
        session.bookmarks.add(mark)
        showMarks()
        save()
        ask(getString(R.string.bookmark_at, TimeFormat.long(mark.time)), "") { label ->
            mark.label = label
            showMarks()
            save()
        }
    }

    private fun editBookmark(mark: Bookmark) {
        val items = arrayOf(getString(R.string.rename), getString(R.string.first_beat), getString(R.string.remove))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.bookmark_at, TimeFormat.long(mark.time)))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> ask(getString(R.string.bookmark_at, TimeFormat.long(mark.time)), mark.label) {
                        mark.label = it
                        showMarks()
                        save()
                    }
                    1 -> {
                        // (uno solo por archivo)
                        val was = mark.firstBeat
                        session.bookmarks.forEach { it.firstBeat = false }
                        mark.firstBeat = !was
                        showMarks()
                        save()
                    }
                    2 -> {
                        session.bookmarks.remove(mark)
                        showMarks()
                        save()
                    }
                }
            }
            .show()
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
            showMarks()
            save()
        }
    }

    private fun editLoop(loop: Loop) {
        val items = arrayOf(getString(R.string.rename), getString(R.string.remove))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.loop_name, TimeFormat.long(loop.start), TimeFormat.long(loop.end)).substringBefore(':'))
            .setItems(items) { _, which ->
                if (which == 0) ask(loop.label.ifEmpty { getString(R.string.loops) }, loop.label) {
                    loop.label = it
                    showMarks()
                    save()
                } else {
                    session.loops.remove(loop)
                    showMarks()
                    save()
                }
            }
            .show()
    }

    private fun row(left: String, right: String, tap: () -> Unit, hold: () -> Unit, highlight: Boolean = false): View {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(44)
            val attrs = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            background = attrs.getDrawable(0)
            attrs.recycle()
            setOnClickListener { tap() }
            setOnLongClickListener { hold(); true }
        }
        val time = TextView(this).apply {
            text = left
            fontFeatureSettings = "tnum"
            setPadding(0, 0, dp(16), 0)
            if (highlight) setTextColor(android.graphics.Color.rgb(0xe0, 0x5a, 0x4f))
        }
        val label = TextView(this).apply {
            text = right
            setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
        }
        view.addView(time)
        view.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return view
    }

    private fun showMarks() {
        session.bookmarks.sortBy { it.time }
        waveform.bookmarks = session.bookmarks.toList()
        bookmarkList.removeAllViews()
        for (mark in session.bookmarks) {
            val label = mark.label.ifEmpty { if (mark.firstBeat) getString(R.string.first_beat) else "" }
            bookmarkList.addView(row(TimeFormat.long(mark.time), label, { seek(mark.time) }, { editBookmark(mark) }, mark.firstBeat))
        }
        loopList.removeAllViews()
        loopsHeading.visibility = if (session.loops.isEmpty()) View.GONE else View.VISIBLE
        for (loop in session.loops.sortedWith(compareBy({ it.start }, { it.end }))) {
            loopList.addView(row("${TimeFormat.long(loop.start)} – ${TimeFormat.long(loop.end)}", loop.label, {
                setSelection(loop.start to loop.end)
                setRepeat(true)
                seek(loop.start)
            }, { editLoop(loop) }))
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
        for (id in listOf(R.id.save, R.id.close)) menu.findItem(id).isEnabled = loaded
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
            R.id.about -> AlertDialog.Builder(this)
                .setTitle(R.string.about)
                .setMessage(getString(R.string.about_text, version))
                .setPositiveButton(R.string.ok, null)
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
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
            KeyEvent.KEYCODE_DPAD_UP -> if (ctrl) setPitch(semitones() + 1, cents()) else setSpeed(session.speed + 0.05)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (ctrl) setPitch(semitones() - 1, cents()) else setSpeed(session.speed - 0.05)
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS -> setSpeed(session.speed + 0.05)
            KeyEvent.KEYCODE_MINUS -> setSpeed(session.speed - 0.05)
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
}
