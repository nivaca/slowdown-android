// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * La onda del archivo, con una regla de tiempo arriba, la posición, la selección
 * y los marcadores.
 *
 * Un toque lleva la posición allí; arrastrar desplaza la vista; pellizcar acerca
 * o aleja alrededor de los dedos. Mantener el dedo y arrastrar selecciona un
 * trecho; mantenerlo sin moverse pone un marcador en ese punto. Mientras suena,
 * la vista sigue a la posición, página a página.
 */
package io.github.nivaca.slowdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
    private val ruler = 22 * density
    private val accent = context.getColor(android.R.color.system_accent1_400)
    private val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; strokeWidth = 1f }
    private val playhead = Paint().apply { color = context.getColor(android.R.color.system_accent3_300); strokeWidth = 2 * density }
    private val selectionPaint = Paint().apply { color = Color.argb(70, 255, 255, 255) }
    private val repeatPaint = Paint().apply { color = context.getColor(android.R.color.system_accent2_300); alpha = 90 }
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xf2, 0xb1, 0x34); strokeWidth = 1.5f * density }
    private val beatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xe0, 0x5a, 0x4f); strokeWidth = 1.5f * density }
    private val tick = Paint().apply { color = Color.argb(140, 200, 200, 200); strokeWidth = density }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 220, 220, 220); textSize = 11 * density }
    private val background = context.getColor(android.R.color.system_neutral1_900)
    private val rulerBackground = context.getColor(android.R.color.system_neutral1_800)
    private var columns = FloatArray(0)
    private val flag = Path()

    fun showAll() {
        if (duration > 0 && width > 0) {
            start = 0.0
            spp = duration / width
        }
        invalidate()
    }

    private fun maxSpp() = if (width > 0 && duration > 0) duration / width * 1.0 else 1.0
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
        val h = height
        canvas.drawColor(background)
        canvas.drawRect(0f, 0f, w.toFloat(), ruler, Paint().apply { color = rulerBackground })
        if (duration <= 0) return
        val top = ruler
        val middle = (top + h) / 2f
        val half = (h - top) / 2f * 0.95f

        // La selección, debajo de la onda
        selection?.let { (a, b) ->
            canvas.drawRect(max(0f, xOf(a)), top, min(w.toFloat(), xOf(b)), h.toFloat(),
                if (repeating) repeatPaint else selectionPaint)
        }

        // La onda: el mínimo y el máximo de cada columna
        if (columns.size != 2 * w) columns = FloatArray(2 * w)
        Engine.peaks(start * rate, spp * rate, columns)
        for (x in 0 until w) {
            val lo = columns[2 * x]
            val hi = columns[2 * x + 1]
            if (lo == 0f && hi == 0f) continue
            canvas.drawLine(x + 0.5f, middle - hi * half, x + 0.5f, middle - lo * half + 1f, wave)
        }

        drawRuler(canvas, w)

        // Los marcadores: una línea y una banderita
        for (mark in bookmarks) {
            val x = xOf(mark.time)
            if (x < -10 || x > w + 10) continue
            val paint = if (mark.firstBeat) beatPaint else markPaint
            canvas.drawLine(x, top, x, h.toFloat(), paint)
            flag.reset()
            flag.moveTo(x, top)
            flag.lineTo(x + 8 * density, top + 5 * density)
            flag.lineTo(x, top + 10 * density)
            flag.close()
            canvas.drawPath(flag, paint)
        }

        // La posición
        val x = xOf(position)
        if (x in 0f..w.toFloat()) canvas.drawLine(x, 0f, x, h.toFloat(), playhead)
    }

    private fun drawRuler(canvas: Canvas, w: Int) {
        // Una marca con su tiempo cada tantos segundos (1, 2, 5 × 10ⁿ), de modo que quepan las etiquetas
        val minimum = 70 * density * spp
        val power = 10.0.pow(floor(log10(minimum)))
        val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * power }.first { it >= minimum }
        var t = floor(start / step) * step
        val end = start + w * spp
        while (t <= end) {
            val x = xOf(t)
            canvas.drawLine(x, ruler - 6 * density, x, ruler, tick)
            canvas.drawText(TimeFormat.short(t, step), x + 3 * density, ruler - 8 * density, label)
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
