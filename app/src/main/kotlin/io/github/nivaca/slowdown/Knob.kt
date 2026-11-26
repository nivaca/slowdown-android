// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * Una perilla como las de py-slowdown (su clase Knob): una raya corta en el
 * borde de la cara, el valor escrito en el centro, marcas alrededor (más larga
 * la de arriba). TOP es el valor que queda arriba: los de abajo se reparten la
 * mitad izquierda y los de arriba la derecha, de modo que la escala no tiene
 * por qué ser pareja (la velocidad: de 25 a 100 % a la izquierda, de 100 a
 * 300 % a la derecha).
 *
 * Con el dedo: arrastrar hacia arriba o a la derecha la sube, hacia abajo o a
 * la izquierda la baja (en el ordenador la raya sigue al ratón, pero una
 * perilla pequeña queda bajo el dedo); un doble toque la devuelve a HOME, y
 * mantener el dedo sin moverlo abre su menú (onMenu: sus valores de costumbre).
 * Arrastrada, se detiene en los valores de DETENTS cuando pasa a menos de
 * DETENT grados de uno (la velocidad, en las elegidas). Los valores por encima
 * de WARN se dibujan en rojo.
 */
package io.github.nivaca.slowdown

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class Knob(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var minimum = 0
    var maximum = 100
    var top: Int? = null  // (sin TOP, el valor del medio: la escala pareja)
    var home = 0
    var ticks: List<Int> = emptyList()
    var warn: Int? = null
        set(value) { field = value; invalidate() }
    var format: (Int) -> String = { "$it" }
    var onChange: ((Int) -> Unit)? = null
    var onMenu: ((Knob) -> Unit)? = null
    var detents: List<Int> = emptyList()  // donde se detiene al arrastrarla

    var value = 0
        private set

    /** Sets the value (within its range) without telling onChange. */
    fun show(v: Int) {
        val clamped = v.coerceIn(minimum, maximum)
        if (clamped != value) {
            value = clamped
            invalidate()
        }
        contentDescription = format(value)
    }

    private fun set(v: Int) {
        val before = value
        show(v)
        if (value != before) onChange?.invoke(value)
    }

    private val palette = Palette(context)
    private val density = resources.displayMetrics.density
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = density }
    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = density }
    private val pointer = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3 * density; strokeCap = Paint.Cap.ROUND }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 12 * density }

    private fun top() = (top ?: ((minimum + maximum) / 2)).toDouble()

    /** The angle of V, in degrees counterclockwise from the right: 240 at the minimum, 90 (straight up) at TOP,
     * -60 at the maximum. */
    fun degrees(v: Double): Double {
        val t = top()
        return if (v <= t) {
            val share = if (t > minimum) (v - minimum) / (t - minimum) else 1.0
            LOWEST - share * (LOWEST - 90.0)
        } else {
            val share = if (maximum > t) (v - t) / (maximum - t) else 0.0
            90.0 - share * (90.0 - HIGHEST)
        }
    }

    /** The value of dragging to V: V, or a detent within DETENT degrees of it. */
    fun detained(v: Int): Int {
        val here = degrees(v.toDouble())
        return detents.filter { it in minimum..maximum && kotlin.math.abs(degrees(it.toDouble()) - here) <= DETENT }
            .minByOrNull { kotlin.math.abs(degrees(it.toDouble()) - here) } ?: v
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = min(width, height) / 2f - density
        val radius = outer - 6 * density
        fun x(deg: Double, r: Float) = cx + r * cos(Math.toRadians(deg)).toFloat()
        fun y(deg: Double, r: Float) = cy - r * sin(Math.toRadians(deg)).toFloat()

        // Las marcas alrededor, más larga la de arriba
        tickPaint.color = palette.text
        tickPaint.alpha = if (isEnabled) 150 else 70
        val t = top()
        for (v in ticks) {
            if (v < minimum || v > maximum) continue
            val d = degrees(v.toDouble())
            val inner = radius + (if (v.toDouble() == t) 1 else 3) * density
            canvas.drawLine(x(d, inner), y(d, inner), x(d, outer), y(d, outer), tickPaint)
        }
        // La cara
        face.color = palette.button
        canvas.drawCircle(cx, cy, radius, face)
        rim.color = palette.text
        rim.alpha = 90
        canvas.drawCircle(cx, cy, radius, rim)
        // El indicador, en el borde de la cara; en rojo más allá de WARN
        val loud = warn?.let { value > it } == true && isEnabled
        pointer.color = if (!isEnabled) palette.faint else if (loud) palette.warning else palette.wave
        val d = degrees(value.toDouble())
        canvas.drawLine(x(d, radius * 0.80f), y(d, radius * 0.80f), x(d, radius - density), y(d, radius - density), pointer)
        // El valor, en el centro
        label.color = if (!isEnabled) palette.faint else if (loud) palette.warning else palette.text
        label.typeface = if (loud) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        canvas.drawText(format(value), cx, cy - (label.descent() + label.ascent()) / 2, label)
    }

    // --- el dedo

    private var startX = 0f
    private var startY = 0f
    private var startValue = 0
    private var dragging = false

    // Cuántos valores por píxel arrastrado: todo el recorrido en unos 200 dp
    private fun perPixel() = (maximum - minimum) / (200 * density)

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            set(home)
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (dragging || onMenu == null) return
            menuShown = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onMenu?.invoke(this@Knob)
        }
    })
    private var menuShown = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        gestures.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                startValue = value
                dragging = false
                menuShown = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val moved = (event.x - startX) + (startY - event.y)
                if (!dragging && !menuShown && kotlin.math.abs(moved) > 4 * density) dragging = true
                if (dragging) {
                    val before = value
                    set(detained((startValue + (moved * perPixel()).roundToInt()).coerceIn(minimum, maximum)))
                    if (value != before && (value in ticks || value in detents)) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (SIZE_DP * density).roundToInt()
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    companion object {
        const val LOWEST = 240.0
        const val HIGHEST = -60.0
        const val SIZE_DP = 56
        const val DETENT = 4.0  // grados: a esa distancia de un valor de DETENTS, la perilla arrastrada se queda en él
    }
}
