// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * La onda del archivo, como en py-slowdown: una regla de tiempo arriba, la
 * franja de los nombres de los marcadores, la onda (más oscura donde ya se
 * tocó, a la escala del pico más alto), la posición en rojo, la selección en
 * verde y los marcadores en naranja, con líneas de trazos.
 *
 * Un toque lleva la posición allí; arrastrar desplaza la vista; pellizcar acerca
 * o aleja alrededor de los dedos. Mantener el dedo y arrastrar selecciona un
 * trecho; mantenerlo sin moverse pone un marcador en ese punto. Mientras suena,
 * la vista sigue a la posición, página a página.
 */
package io.github.nivaca.slowdown

import android.content.Context
import android.graphics.Canvas
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
    var follow = true  // la vista sigue a la posición

    // La vista: el segundo del borde izquierdo y los segundos por píxel
    private var start = 0.0
    private var spp = 0.01

    private val density = resources.displayMetrics.density
    private val ruler = 22 * density  // la regla de tiempo
    private val flagHeight = 18 * density  // la franja de los nombres de los marcadores, bajo la regla
    private val palette = Palette(context)
    private val fill = Paint()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4 * density, 3 * density), 0f)
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11 * density }
    private var columns = FloatArray(0)

    fun showAll() {
        if (duration > 0 && width > 0) {
            start = 0.0
            spp = duration / width
        }
        invalidate()
    }

    private fun maxSpp() = if (width > 0 && duration > 0) duration / width else 1.0
    private fun minSpp() = 4.0 / rate  // (cuatro cuadros por píxel, de cerca)

    fun zoom(factor: Double, focusX: Float = width / 2f) {
        val at = start + focusX * spp
        spp = (spp / factor).coerceIn(minSpp(), maxSpp())
        start = at - focusX * spp
        clampStart()
        invalidate()
    }

    private fun clampStart() {
        start = start.coerceIn(0.0, max(0.0, duration - width * spp))
    }

    private fun xOf(seconds: Double) = ((seconds - start) / spp).toFloat()
    private fun secondsAt(x: Float) = (start + x * spp).coerceIn(0.0, duration)

    /** Moves the view so that the position is in it, a page at a time, while it plays. */
    fun update(seconds: Double) {
        position = seconds
        if (follow && width > 0 && !dragging) {
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

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height.toFloat()
        canvas.drawColor(palette.base)
        fill.color = palette.ruler
        canvas.drawRect(0f, 0f, w.toFloat(), ruler, fill)
        if (duration <= 0) return
        drawRuler(canvas, w)
        drawWave(canvas, w, ruler + flagHeight, h - ruler - flagHeight)
        drawSelection(canvas, h)
        drawMarks(canvas, h)
        // La posición, en rojo
        line.color = palette.cursor
        line.strokeWidth = 2 * density
        val x = xOf(position)
        canvas.drawLine(x, ruler, x, h, line)
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
            }
        }
    }

    private fun drawSelection(canvas: Canvas, h: Float) {
        val (a, b) = selection ?: return
        val x0 = xOf(a)
        val x1 = xOf(b)
        fill.color = palette.select
        fill.alpha = if (repeating) 90 else 45
        canvas.drawRect(x0, ruler, x1, h, fill)
        line.color = palette.select
        line.strokeWidth = (if (repeating) 2 else 1) * density
        canvas.drawLine(x0, ruler, x0, h, line)
        canvas.drawLine(x1, ruler, x1, h, line)
        fill.alpha = 255
        canvas.drawRect(x0, 0f, x1, 4 * density, fill)  // (también en la regla)
    }

    private fun drawMarks(canvas: Canvas, h: Float) {
        bookmarks.forEachIndexed { i, mark ->
            val x = xOf(mark.time)
            if (x < -200 * density || x > width + 2) return@forEachIndexed
            // El primer pulso, del color de la selección y con línea entera; los otros, del de los marcadores
            val color = if (mark.firstBeat) palette.select else palette.mark
            if (mark.firstBeat) {
                line.color = color
                line.strokeWidth = density
                canvas.drawLine(x, ruler, x, h, line)
            } else {
                dashed.color = color
                dashed.strokeWidth = density
                canvas.drawLine(x, ruler, x, h, dashed)
            }
            val text = if (mark.firstBeat) (if (mark.label.isEmpty()) "1" else "1 · ${mark.label}")
            else mark.label.ifEmpty { "${i + 1}" }
            val shown = TextUtils.ellipsize(text, TextPaint(label), 160 * density, TextUtils.TruncateAt.END).toString()
            val boxWidth = label.measureText(shown) + 8 * density
            fill.color = color
            canvas.drawRect(x, ruler + density, x + boxWidth, ruler + flagHeight - density, fill)
            label.color = palette.base
            canvas.drawText(shown, x + 4 * density, ruler + flagHeight / 2 - (label.descent() + label.ascent()) / 2, label)
        }
    }

    private fun drawRuler(canvas: Canvas, w: Int) {
        // Una marca con su tiempo cada tantos segundos (1, 2, 5 × 10ⁿ), de modo que quepan las etiquetas, y cuatro
        // marcas menores entre una y otra
        val minimum = 70 * density * spp
        val power = 10.0.pow(floor(log10(minimum)))
        val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * power }.first { it >= minimum }
        line.color = palette.faint
        line.strokeWidth = 1f
        canvas.drawLine(0f, ruler - 1, w.toFloat(), ruler - 1, line)
        label.color = palette.text
        var t = floor(start / step) * step
        val end = start + w * spp
        while (t <= end + step) {
            val x = xOf(t)
            canvas.drawLine(x, ruler - 6 * density, x, ruler - 1, line)
            for (k in 1..4) {
                val xm = xOf(t + k * step / 5)
                canvas.drawLine(xm, ruler - 3 * density, xm, ruler - 1, line)
            }
            canvas.drawText(TimeFormat.short(t, step), x + 3 * density, -label.ascent() + 2 * density, label)
            t += step
        }
    }

    // --- los gestos

    private var dragging = false
    private var selecting = false
    private var anchor = 0.0
    private var scaling = false

    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
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
            if (duration > 0) listener?.onSeek(secondsAt(e.x))
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
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
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }
}
