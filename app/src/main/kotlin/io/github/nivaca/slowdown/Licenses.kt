// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * Lo que lleva el APK de otros, con su versión, sus autores y su licencia:
 * la ventana lo muestra (menú > Licencias), con el texto entero de cada
 * licencia, que va en los assets (LICENSES/ del código). docs/THIRD-PARTY.md
 * dice lo mismo, y para qué sirve cada cosa; una prueba comprueba que las
 * dos listas y los textos no se separen.
 */
package io.github.nivaca.slowdown

/** A piece of software that the APK carries: its name and version, who holds its copyright, its licence as an SPDX
 * expression, and the files of LICENSES/ with the full texts. */
data class Component(
    val name: String,
    val version: String,
    val holder: String,
    val license: String,
    val files: List<String>,
    val url: String,
)

object Licenses {
    val components = listOf(
        Component("Slowdown", "", "© 2026 Nicolas Vaughan", "GPL-3.0-or-later",
            listOf("GPL-3.0-or-later.txt"), "https://github.com/nivaca/slowdown-android"),
        Component("Rubber Band Library", "4.0.0", "© 2007–2024 Particular Programs Ltd.",
            "GPL-2.0-or-later", listOf("GPL-2.0-or-later.txt"), "https://breakfastquay.com/rubberband/"),
        Component("minimp3", "", "lieff, dedicated to the public domain", "CC0-1.0",
            listOf("CC0-1.0.txt"), "https://github.com/lieff/minimp3"),
        Component("dr_flac", "0.13.4", "© 2023 David Reid", "Unlicense OR MIT-0",
            listOf("Unlicense.txt", "MIT-0.txt"), "https://github.com/mackron/dr_libs"),
        Component("dr_wav", "0.14.6", "© 2023 David Reid", "Unlicense OR MIT-0",
            listOf("Unlicense.txt", "MIT-0.txt"), "https://github.com/mackron/dr_libs"),
        Component("Kotlin standard library", "2.2.10", "© JetBrains s.r.o. and Kotlin contributors",
            "Apache-2.0", listOf("Apache-2.0.txt"), "https://kotlinlang.org"),
        Component("LLVM libc++, libc++abi (Android NDK r29)", "", "The LLVM Project contributors",
            "Apache-2.0 WITH LLVM-exception", listOf("LLVM-libcxx.txt"), "https://libcxx.llvm.org"),
        Component("Material Icons (the shapes of the buttons)", "", "© Google LLC", "Apache-2.0",
            listOf("Apache-2.0.txt"), "https://github.com/google/material-design-icons"),
    )

    /** TEXT for a narrow screen: the lines of each paragraph joined, so that it wraps to the width (the texts
     * break their lines at about 72 characters); paragraphs, and the lines of a heading or a list, stay apart. */
    fun reflow(text: String): String =
        text.replace("\r\n", "\n").split(Regex("\n[ \t]*\n")).joinToString("\n\n") { paragraph ->
            val lines = paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val out = StringBuilder()
            var afterHeading = false
            for (line in lines) {
                // Un título corto en mayúsculas va en su renglón, y también una línea que empieza un punto
                val heading = line.length < 60 && line == line.uppercase() && line.any { it.isLetter() }
                val item = Regex("^([0-9]+\\.|\\([a-z0-9]+\\)|[-*•])\\s").containsMatchIn(line)
                if (out.isNotEmpty()) out.append(if (heading || item || afterHeading) "\n" else " ")
                out.append(line)
                afterHeading = heading
            }
            out.toString()
        }
}
