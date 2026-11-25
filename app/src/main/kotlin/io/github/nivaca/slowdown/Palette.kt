// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * Los colores de la onda y de las perillas: los de py-slowdown (waveform.py),
 * claros u oscuros según el tema del teléfono.
 */
package io.github.nivaca.slowdown

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

class Palette(context: Context) {
    val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    // Lo del tema: el fondo de la onda, el de la regla, el texto y lo tenue
    val base = if (dark) Color.rgb(0x1e, 0x21, 0x24) else Color.WHITE
    val ruler = if (dark) Color.rgb(0x2a, 0x2e, 0x32) else Color.rgb(0xef, 0xf0, 0xf1)
    val text = if (dark) Color.rgb(0xe8, 0xe8, 0xe8) else Color.rgb(0x23, 0x26, 0x29)
    val faint = if (dark) Color.rgb(0x8a, 0x8f, 0x94) else Color.rgb(0x9a, 0x9e, 0xa2)
    val button = if (dark) Color.rgb(0x34, 0x38, 0x3c) else Color.rgb(0xf4, 0xf5, 0xf6)

    // El resaltado del tema (el de Fusion), y la parte ya tocada: más oscura en el tema claro, más clara en el oscuro
    val wave = Color.rgb(0x30, 0x8c, 0xc6)
    val played = if (dark) Color.rgb(0x7f, 0xc4, 0xf0) else Color.rgb(0x1e, 0x57, 0x7c)
    val cursor = if (dark) Color.rgb(0xff, 0x5a, 0x3c) else Color.rgb(0xd6, 0x2d, 0x0f)
    val mark = if (dark) Color.rgb(0xf2, 0xb1, 0x34) else Color.rgb(0xb8, 0x73, 0x0a)
    val select = if (dark) Color.rgb(0x4c, 0xc3, 0x8a) else Color.rgb(0x1f, 0x8a, 0x5b)
    val warning = Color.rgb(200, 30, 30)  // lo que pasa de lo normal (el volumen reforzado)
}
