// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * Lo que hace el ecualizador, como una curva (la EqualizerCurve de py-slowdown):
 * la ganancia de 20 Hz a 20 kHz, en escala logarítmica, de −12 a +12 dB, con
 * una línea en 0 y una en cada banda; gris cuando está apagado.
 */
package io.github.nivaca.slowdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

class EqCurve(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var gains: List<Int> = Equalizer.FLAT
        set(value) { field = value; invalidate() }
    var on = true
        set(value) { field = value; invalidate() }

    private val palette = Palette(context)
    private val density = resources.displayMetrics.density
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }
    private val curve = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawColor(palette.base)
        val low = log10(20.0)
        val high = log10(20000.0)
        fun xOf(freq: Double) = ((log10(freq) - low) / (high - low) * (w - 1)).toFloat()
        fun yOf(db: Double) = (h / 2 - db / Equalizer.MAX_GAIN * (h / 2 - 4 * density)).toFloat()
        grid.color = palette.faint
        grid.alpha = 90
        canvas.drawLine(0f, yOf(0.0), w, yOf(0.0), grid)
        grid.alpha = 50
        for (band in Equalizer.BANDS) canvas.drawLine(xOf(band.toDouble()), 0f, xOf(band.toDouble()), h, grid)
        path.reset()
        val steps = maxOf(2, (w / (2 * density)).roundToInt())
        for (i in 0..steps) {
            val freq = 10.0.pow(low + (high - low) * i / steps)
            val x = xOf(freq)
            val y = yOf(Equalizer.response(gains, freq))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        curve.color = if (on) palette.wave else palette.faint
        canvas.drawPath(path, curve)
    }
}
