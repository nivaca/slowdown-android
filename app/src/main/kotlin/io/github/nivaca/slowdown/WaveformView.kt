// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * La onda del archivo, como en py-slowdown: una regla de tiempo arriba, la
 * franja de los nombres de los marcadores, la onda (más oscura donde ya se
 * tocó, a la escala del pico más alto), la posición en rojo, la selección en
 * verde y los marcadores en naranja, con líneas de trazos. Al pie, los bucles
 * guardados, cada uno una banda de su color con su nombre; de cerca, arriba,
 * una franja con el archivo entero, la parte que se ve sombreada.
 *
 * Un toque lleva la posición allí; arrastrar desplaza la vista; pellizcar acerca
 * o aleja alrededor de los dedos. Mantener el dedo y arrastrar selecciona un
 * trecho; mantenerlo sin moverse pone un marcador en ese punto. Un toque en un
 * bucle lo elige; en la franja del archivo entero (o arrastrando en ella), se ve
 * esa parte, sin mover la posición. Mientras suena, la vista sigue a la
 * posición, página a página.
 *
 * Sin un archivo, muestra las grabaciones abiertas hace poco, cada una con una
 * figura pequeña de su onda; un toque abre una.
 */
package io.github.nivaca.slowdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class WaveformView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        fun onSeek(seconds: Double)
        fun onSelect(start: Double, end: Double)
        fun onBookmarkAt(seconds: Double)
        fun onLoop(loop: Loop)
        fun onRecent(recent: Recent)
    }

    var listener: Listener? = null
    var duration = 0.0
        set(value) {
            val first = field <= 0.0
            field = value
            if (first) showAll()
            invalidate()
        }
    var rate = 44100
    var position = 0.0
    var selection: Pair<Double, Double>? = null
        set(value) { field = value; invalidate() }
    var repeating = false
        set(value) { field = value; invalidate() }
    var bookmarks: List<Bookmark> = emptyList()
        set(value) { field = value; invalidate() }
    var loops: List<Loop> = emptyList()
        set(value) { field = value; invalidate() }
    var follow = true  // la vista sigue a la posición
    var count: Int? = null  // el pulso que suena en la cuenta previa, grande, en el centro
        set(value) { if (value != field) { field = value; invalidate() } }

    // La pantalla de inicio: las grabaciones de hace poco, y si cada una está todavía
    var recents: List<Pair<Recent, Boolean>> = emptyList()
        set(value) { field = value; recentScroll = 0f; invalidate() }
    var recentsTitle = ""

    // La vista: el segundo del borde izquierdo y los segundos por píxel
    private var start = 0.0
    private var spp = 0.01

    private val density = resources.displayMetrics.density
    private val ruler = 22 * density  // la regla de tiempo
    private val flagHeight = 18 * density  // la franja de los nombres de los marcadores, bajo la regla
    private val lane = 15 * density  // la de los bucles, al pie
    private val strip = 18 * density  // la del archivo entero, arriba, de cerca
    private val palette = Palette(context)
    private val fill = Paint()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4 * density, 3 * density), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11 * density }
    private var columns = FloatArray(0)
    private var whole = FloatArray(0)

    fun showAll() {
        if (duration > 0 && width > 0) {
            start = 0.0
            spp = duration / width
        }
        invalidate()
    }

    private fun maxSpp() = if (width > 0 && duration > 0) duration / width else 1.0
    private fun minSpp() = 4.0 / rate  // (cuatro cuadros por píxel, de cerca)
    private fun zoomed() = duration > 0 && spp < maxSpp() * 0.98

    fun zoom(factor: Double, focusX: Float = width / 2f) {
        val at = start + focusX * spp
        spp = (spp / factor).coerceIn(minSpp(), maxSpp())
        start = at - focusX * spp
        clampStart()
        invalidate()
    }

    /** Shows from FROM to TO seconds, with a twentieth of that on each side (the whole file, if it is wider). */
    fun zoomTo(from: Double, to: Double) {
        if (duration <= 0 || width <= 0 || to <= from) return
        val margin = (to - from) / 20
        val wanted = ((to - from) + 2 * margin) / width
        if (wanted >= maxSpp()) {
            showAll()
            return
        }
        spp = max(minSpp(), wanted)
        start = from - margin
        clampStart()
        invalidate()
    }

    private fun clampStart() {
        start = start.coerceIn(0.0, max(0.0, duration - width * spp))
    }

    private fun xOf(seconds: Double) = ((seconds - start) / spp).toFloat()
    private fun secondsAt(x: Float) = (start + x * spp).coerceIn(0.0, duration)

    /** Moves the view so that the position is in it, a page at a time, when the position moves. */
    fun update(seconds: Double) {
        val moved = seconds != position
        position = seconds
        if (follow && moved && width > 0 && !dragging && !overviewing) {
            val x = xOf(seconds)
            if (x < 0 || x > width * 0.95f) {
                start = seconds - width * spp * 0.05
                clampStart()
            }
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (oldw == 0 || spp >= maxSpp()) showAll() else clampStart()
    }

    // --- el valor que acaba de cambiar, grande un momento, y luego se desvanece

    private var flashText: String? = null
    private var flashAlpha = 0
    private val fade = object : Runnable {
        override fun run() {
            flashAlpha -= 50
            if (flashAlpha <= 0) flashText = null else postDelayed(this, 40)
            invalidate()
        }
    }

    fun flash(text: String) {
        flashText = text
        flashAlpha = 255
        removeCallbacks(fade)
        postDelayed(fade, 900)
        invalidate()
    }

    // --- el dibujo

    private fun top() = if (zoomed()) strip else 0f  // donde empieza la regla
    private fun bottom() = height - if (loops.isNotEmpty()) lane else 0f  // donde acaba la onda

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height.toFloat()
        canvas.drawColor(palette.base)
        if (duration <= 0) {
            drawRecents(canvas, w, h)
            return
        }
        val top = top()
        val bottom = bottom()
        if (top > 0) drawOverview(canvas, w, top)
        fill.color = palette.ruler
        canvas.drawRect(0f, top, w.toFloat(), top + ruler, fill)
        drawRuler(canvas, w, top)
        drawWave(canvas, w, top + ruler + flagHeight, bottom - top - ruler - flagHeight)
        drawSelection(canvas, top, bottom)
        drawMarks(canvas, top, bottom)
        if (loops.isNotEmpty()) drawLoops(canvas, w, bottom, h)
        // La posición, en rojo
        line.color = palette.cursor
        line.strokeWidth = 2 * density
        val x = xOf(position)
        canvas.drawLine(x, top + ruler, x, bottom, line)
        val beat = count
        if (beat != null) {
            countPaint.color = palette.cursor
            countPaint.alpha = 170
            countPaint.textSize = max(24 * density, (bottom - top - ruler) * 0.45f)
            canvas.drawText("$beat", width / 2f, (top + ruler + bottom) / 2 - (countPaint.descent() + countPaint.ascent()) / 2, countPaint)
        } else {
            flashText?.let { drawFlash(canvas, it, (top + ruler + bottom) / 2) }
        }
    }

    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun drawFlash(canvas: Canvas, text: String, middle: Float) {
        flashPaint.textSize = 30 * density
        val boxW = flashPaint.measureText(text) + 36 * density
        val boxH = flashPaint.descent() - flashPaint.ascent() + 14 * density
        fill.color = palette.ruler
        fill.alpha = (flashAlpha * 0.8).toInt()
        canvas.drawRoundRect(width / 2f - boxW / 2, middle - boxH / 2, width / 2f + boxW / 2, middle + boxH / 2,
            8 * density, 8 * density, fill)
        fill.alpha = 255
        flashPaint.color = palette.text
        flashPaint.alpha = flashAlpha
        canvas.drawText(text, width / 2f, middle - (flashPaint.descent() + flashPaint.ascent()) / 2, flashPaint)
    }

    private fun drawWave(canvas: Canvas, w: Int, top: Float, height: Float) {
        val middle = top + height / 2
        line.color = palette.faint
        line.strokeWidth = 1f
        canvas.drawLine(0f, middle, w.toFloat(), middle, line)
        if (columns.size != 2 * w) columns = FloatArray(2 * w)
        Engine.peaks(start * rate, spp * rate, columns)
        // A la escala del pico del archivo (lo leído hasta ahora): una grabación baja no se ve plana
        val loudest = max(Engine.loudest(), 0.05f)
        val half = (height / 2 - 2) / min(1f, loudest)
        val limit = height / 2 - 2
        val cursor = xOf(position)
        for (x in 0 until w) {
            val lo = columns[2 * x]
            val hi = columns[2 * x + 1]
            if (lo == 0f && hi == 0f) continue
            val yHi = middle - (hi * half).coerceIn(-limit, limit)
            val yLo = max(middle - (lo * half).coerceIn(-limit, limit), yHi + 1)
            // Lo ya tocado, de otro color
            fill.color = if (x < cursor) palette.played else palette.wave
            canvas.drawRect(x.toFloat(), yHi, x + 1f, yLo, fill)
        }
        if (!Engine.done()) {
            // Lo que falta leer: una franja tenue
            val x = xOf(Engine.decoded().toDouble() / rate)
            if (x < w) {
                fill.color = palette.faint
                fill.alpha = 40
                canvas.drawRect(max(0f, x), top, w.toFloat(), top + height, fill)
                fill.alpha = 255
            }
        }
    }

    // El archivo entero, en pequeño: la parte que se ve, sombreada; la posición, en rojo
    private fun drawOverview(canvas: Canvas, w: Int, h: Float) {
        fill.color = palette.ruler
        canvas.drawRect(0f, 0f, w.toFloat(), h, fill)
        if (whole.size != 2 * w) whole = FloatArray(2 * w)
        val fpp = duration * rate / w
        Engine.peaks(0.0, fpp, whole)
        val loudest = max(Engine.loudest(), 0.05f)
        val middle = h / 2
        val half = (h / 2 - density) / min(1f, loudest)
        fill.color = palette.faint
        for (x in 0 until w) {
            val lo = whole[2 * x]
            val hi = whole[2 * x + 1]
            if (lo == 0f && hi == 0f) continue
            canvas.drawRect(x.toFloat(), middle - hi * half, x + 1f, max(middle - lo * half, middle - hi * half + 1), fill)
        }
        val x0 = (start / duration * w).toFloat()
        val x1 = ((start + w * spp) / duration * w).toFloat()
        fill.color = palette.wave
        fill.alpha = 70
        canvas.drawRect(x0, 0f, max(x0 + 2 * density, x1), h, fill)
        fill.alpha = 255
        line.color = palette.cursor
        line.strokeWidth = 1.5f * density
        val xp = (position / duration * w).toFloat()
        canvas.drawLine(xp, 0f, xp, h, line)
        line.color = palette.faint
        line.strokeWidth = 1f
        canvas.drawLine(0f, h - 0.5f, w.toFloat(), h - 0.5f, line)
    }

    private fun drawSelection(canvas: Canvas, top: Float, bottom: Float) {
        val (a, b) = selection ?: return
        val x0 = xOf(a)
        val x1 = xOf(b)
        fill.color = palette.select
        fill.alpha = if (repeating) 90 else 45
        canvas.drawRect(x0, top + ruler, x1, bottom, fill)
        line.color = palette.select
        line.strokeWidth = (if (repeating) 2 else 1) * density
        canvas.drawLine(x0, top + ruler, x0, bottom, line)
        canvas.drawLine(x1, top + ruler, x1, bottom, line)
        fill.alpha = 255
        canvas.drawRect(x0, top, x1, top + 4 * density, fill)  // (también en la regla)
    }

    private fun drawMarks(canvas: Canvas, top: Float, bottom: Float) {
        bookmarks.forEachIndexed { i, mark ->
            val x = xOf(mark.time)
            if (x < -200 * density || x > width + 2) return@forEachIndexed
            // El primer pulso, del color de la selección y con línea entera; los otros, del de los marcadores
            val color = if (mark.firstBeat) palette.select else palette.mark
            if (mark.firstBeat) {
                line.color = color
                line.strokeWidth = density
                canvas.drawLine(x, top + ruler, x, bottom, line)
            } else {
                dashed.color = color
                dashed.strokeWidth = density
                canvas.drawLine(x, top + ruler, x, bottom, dashed)
            }
            val text = if (mark.firstBeat) (if (mark.label.isEmpty()) "1" else "1 · ${mark.label}")
            else mark.label.ifEmpty { "${i + 1}" }
            val shown = TextUtils.ellipsize(text, TextPaint(label), 160 * density, TextUtils.TruncateAt.END).toString()
            val boxWidth = label.measureText(shown) + 8 * density
            fill.color = color
            canvas.drawRect(x, top + ruler + density, x + boxWidth, top + ruler + flagHeight - density, fill)
            label.color = palette.base
            canvas.drawText(shown, x + 4 * density, top + ruler + flagHeight / 2 - (label.descent() + label.ascent()) / 2, label)
        }
    }

    // Los bucles guardados, al pie: cada uno una banda de su color, con su nombre
    private fun sortedLoops() = loops.sortedWith(compareBy({ it.start }, { it.end }))

    private fun drawLoops(canvas: Canvas, w: Int, top: Float, h: Float) {
        fill.color = palette.ruler
        canvas.drawRect(0f, top, w.toFloat(), h, fill)
        for ((i, loop) in sortedLoops().withIndex()) {
            val x0 = max(xOf(loop.start), -2f)
            val x1 = min(xOf(loop.end), w + 2f)
            if (x1 < 0 || x0 > w) continue
            val color = LOOP_COLORS[i % LOOP_COLORS.size]
            fill.color = color
            fill.alpha = 170
            canvas.drawRect(x0, top + density, max(x0 + 2 * density, x1), h - density, fill)
            fill.alpha = 255
            if (loop.label.isNotEmpty() && x1 - x0 > 16 * density) {
                val shown = TextUtils.ellipsize(loop.label, TextPaint(label), x1 - x0 - 6 * density,
                    TextUtils.TruncateAt.END).toString()
                // (el texto blanco o negro, el que se lea mejor sobre el color)
                val light = Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114 > 150
                label.color = if (light) Color.BLACK else Color.WHITE
                canvas.drawText(shown, max(x0, 0f) + 3 * density, (top + h) / 2 - (label.descent() + label.ascent()) / 2, label)
            }
        }
    }

    private fun loopAt(x: Float): Loop? {
        val t = secondsAt(x)
        // (la más corta, si se superponen: la de dentro)
        return sortedLoops().filter { t >= it.start && t <= it.end }.minByOrNull { it.end - it.start }
    }

    private fun drawRuler(canvas: Canvas, w: Int, top: Float) {
        // Una marca con su tiempo cada tantos segundos (1, 2, 5 × 10ⁿ), de modo que quepan las etiquetas, y cuatro
        // marcas menores entre una y otra
        val minimum = 70 * density * spp
        val power = 10.0.pow(floor(log10(minimum)))
        val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * power }.first { it >= minimum }
        line.color = palette.faint
        line.strokeWidth = 1f
        val base = top + ruler
        canvas.drawLine(0f, base - 1, w.toFloat(), base - 1, line)
        label.color = palette.text
        var t = floor(start / step) * step
        val end = start + w * spp
        while (t <= end + step) {
            val x = xOf(t)
            canvas.drawLine(x, base - 6 * density, x, base - 1, line)
            for (k in 1..4) {
                val xm = xOf(t + k * step / 5)
                canvas.drawLine(xm, base - 3 * density, xm, base - 1, line)
            }
            canvas.drawText(TimeFormat.short(t, step), x + 3 * density, top - label.ascent() + 2 * density, label)
            t += step
        }
    }

    // --- la pantalla de inicio

    private val row = 40 * density
    private val heading = 26 * density
    private var recentScroll = 0f

    private fun drawRecents(canvas: Canvas, w: Int, h: Float) {
        if (recents.isEmpty()) return
        label.color = palette.faint
        label.textSize = 12 * density
        canvas.drawText(recentsTitle, 12 * density, heading / 2 - (label.descent() + label.ascent()) / 2, label)
        canvas.save()
        canvas.clipRect(0f, heading, w.toFloat(), h)
        val picture = 96 * density
        val name = TextPaint(Paint.ANTI_ALIAS_FLAG)
        name.textSize = 14 * density  // (no dentro de apply: TextPaint tiene su propio «density»)
        for ((i, entry) in recents.withIndex()) {
            val (recent, here) = entry
            val top = heading + i * row - recentScroll
            if (top > h || top + row < heading) continue
            val middle = top + row / 2
            // La figura de su onda
            fill.color = palette.ruler
            canvas.drawRect(12 * density, top + 4 * density, 12 * density + picture, top + row - 4 * density, fill)
            fill.color = if (here) palette.wave else palette.faint
            val columnWidth = picture / recent.summary.size
            val half = row / 2 - 7 * density
            for ((k, v) in recent.summary.withIndex()) {
                val x = 12 * density + k * columnWidth
                canvas.drawRect(x, middle - v * half - 0.5f, x + max(1f, columnWidth), middle + v * half + 0.5f, fill)
            }
            // Su nombre (gris si ya no está)
            name.color = if (here) palette.text else palette.faint
            val text = TextUtils.ellipsize(recent.name, name, w - picture - 36 * density, TextUtils.TruncateAt.END).toString()
            canvas.drawText(text, picture + 24 * density, middle - (name.descent() + name.ascent()) / 2, name)
            line.color = palette.ruler
            line.strokeWidth = 1f
            canvas.drawLine(12 * density, top + row, w - 12 * density, top + row, line)
        }
        canvas.restore()
        label.textSize = 11 * density
    }

    private fun recentAt(y: Float): Pair<Recent, Boolean>? {
        if (y < heading) return null
        return recents.getOrNull(((y - heading + recentScroll) / row).toInt())
    }

    private fun maxRecentScroll() = max(0f, heading + recents.size * row - height)

    // --- los gestos

    private var dragging = false
    private var selecting = false
    private var overviewing = false  // el dedo en la franja del archivo entero
    private var anchor = 0.0
    private var scaling = false

    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (duration <= 0) return false
            scaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom(detector.scaleFactor.toDouble(), detector.focusX)
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (duration <= 0) {
                recentAt(e.y)?.let { (recent, here) -> if (here) listener?.onRecent(recent) }
                return true
            }
            if (loops.isNotEmpty() && e.y >= bottom()) {
                loopAt(e.x)?.let { listener?.onLoop(it); return true }
            }
            listener?.onSeek(secondsAt(e.x))
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (duration <= 0) {
                recentScroll = (recentScroll + dy).coerceIn(0f, maxRecentScroll())
                invalidate()
                return true
            }
            if (scaling || selecting) return false
            dragging = true
            start += dx * spp
            clampStart()
            invalidate()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (duration <= 0 || scaling) return
            selecting = true
            anchor = secondsAt(e.x)
            selectionMoved = false
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    })
    private var selectionMoved = false

    // La vista, centrada en el punto X de la franja del archivo entero
    private fun showAround(x: Float) {
        val span = width * spp
        start = (x / width * duration) - span / 2
        clampStart()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        if (event.actionMasked == MotionEvent.ACTION_DOWN && zoomed() && event.y < strip) overviewing = true
        if (overviewing) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> showAround(event.x)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> overviewing = false
            }
            return true
        }
        scale.onTouchEvent(event)
        if (selecting) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    val here = secondsAt(event.x)
                    if (abs(xOf(here) - xOf(anchor)) > 8 * density) selectionMoved = true
                    if (selectionMoved) selection = min(anchor, here) to max(anchor, here)
                    // (cerca de los bordes, la vista se corre)
                    if (event.x < 20 * density) { start -= 10 * spp; clampStart() }
                    if (event.x > width - 20 * density) { start += 10 * spp; clampStart() }
                }
                MotionEvent.ACTION_UP -> {
                    selecting = false
                    if (selectionMoved) selection?.let { listener?.onSelect(it.first, it.second) }
                    else listener?.onBookmarkAt(anchor)
                }
                MotionEvent.ACTION_CANCEL -> selecting = false
            }
            return true
        }
        gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            dragging = false
            scaling = false
        }
        return true
    }

    companion object {
        // Los colores de los bucles, uno tras otro (de la paleta de Okabe e Ito, como en py-slowdown: se distinguen
        // también sin ver bien los colores)
        val LOOP_COLORS = intArrayOf(0xffe69f00.toInt(), 0xff56b4e9.toInt(), 0xff009e73.toInt(), 0xffcc79a7.toInt(),
            0xffd55e00.toInt(), 0xff0072b2.toInt(), 0xfff0e442.toInt())
    }
}
