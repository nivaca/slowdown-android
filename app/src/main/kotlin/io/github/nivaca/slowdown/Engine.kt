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

    /** How the sound is heard: CHANNELS 0 both, 1 left, 2 right, 3 mono, 4 without the centre; FILTER 0 none,
     * 1 only the bass, 2 without the bass. */
    external fun setHearing(channels: Int, filter: Int)

    /** The equalizer: the gains of its five bands (60 Hz, 250 Hz, 1 kHz, 4 kHz, 12 kHz), from -12 to +12 dB;
     * all 0, nothing. */
    external fun setEqualizer(gains: IntArray)

    /** Repeat from START to END seconds, without a gap; START < 0: no longer. */
    external fun setLoop(start: Double, end: Double)
    external fun looping(): Boolean

    /** Exports to FD (FORMAT 0 WAV, 1 M4A) from START to END seconds (END < 0: to the end), in a thread; null, or
     * why it could not start. */
    external fun exportStart(fd: Int, format: Int, start: Double, end: Double, speed: Double, pitch: Double,
                             formants: Boolean, quality: Int, channels: Int, filter: Int, eq: IntArray,
                             gain: Double): String?
    external fun exportProgress(): Double
    external fun exportDone(): Boolean
    external fun exportError(): String?
    external fun exportCancel()
    external fun exportRelease()

    // El pulso que suena por 100 más cuántos son, o -1
    external fun counting(): Int
    external fun maxEarlier(rate: Int, bpm: Double): Double

    fun clampSpeed(speed: Double): Double = (Math.round(speed * 1000) / 1000.0).coerceIn(MIN_SPEED, MAX_SPEED)
}
