// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * El motor, en C++ (libslowdown.so): la decodificación, el estirador de Rubber Band
 * y la salida de sonido. Hay uno solo para toda la aplicación.
 */
package io.github.nivaca.slowdown

object Engine {
    init {
        System.loadLibrary("slowdown")
    }

    const val MIN_SPEED = 0.25
    const val MAX_SPEED = 3.0
    const val MAX_PITCH = 12.0

    const val QUALITY_HIGH = 0
    const val QUALITY_LIGHT = 1
    const val QUALITY_R2 = 2

    /** Opens the file at FD (from OFFSET, LENGTH bytes, or -1 for the rest); null, or why it could not. */
    external fun nativeOpen(fd: Int, offset: Long, length: Long): String?
    external fun nativeClose()

    external fun rate(): Int
    external fun channels(): Int
    external fun decoded(): Long
    external fun done(): Boolean
    external fun failed(): Boolean
    external fun duration(): Double
    external fun loudest(): Float

    /** The minimum and maximum of OUT.size / 2 columns of FPP frames each, from the frame START. */
    external fun peaks(start: Double, fpp: Double, out: FloatArray)

    external fun play(beats: Int, bpm: Double, offset: Double)
    external fun pause()
    external fun stop()
    external fun seek(seconds: Double)
    external fun playing(): Boolean
    external fun takeEnded(): Boolean
    external fun position(): Double

    external fun setSpeed(speed: Double)
    external fun setPitch(semitones: Double)
    external fun setFormants(on: Boolean)
    external fun setQuality(quality: Int)
    external fun setVolume(volume: Double)

    /** Repeat from START to END seconds, without a gap; START < 0: no longer. */
    external fun setLoop(start: Double, end: Double)
    external fun looping(): Boolean

    // El pulso que suena por 100 más cuántos son, o -1
    external fun counting(): Int
    external fun maxEarlier(rate: Int, bpm: Double): Double

    fun clampSpeed(speed: Double): Double = (Math.round(speed * 1000) / 1000.0).coerceIn(MIN_SPEED, MAX_SPEED)
}
