// SPDX-License-Identifier: GPL-3.0-or-later
// Las licencias: lo que la ventana muestra (Licenses.kt), los textos de LICENSES/ y los documentos de docs/
// no se separan.
package io.github.nivaca.slowdown

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LicensesTest {
    // (las pruebas corren en la carpeta app/)
    private val root = File("..").canonicalFile
    private val folder = File(root, "LICENSES")

    @Test
    fun everyLicenceHasItsText() {
        for (c in Licenses.components) {
            assertTrue("${c.name}: no files", c.files.isNotEmpty())
            for (f in c.files) assertTrue("${c.name}: LICENSES/$f is missing", File(folder, f).isFile)
        }
    }

    @Test
    fun everyTextIsUsed() {
        val used = Licenses.components.flatMap { it.files }.toSet()
        for (f in folder.listFiles()!!.filter { it.name.endsWith(".txt") }) {
            assertTrue("LICENSES/${f.name} belongs to no component", f.name in used)
        }
    }

    @Test
    fun theDocumentsNameEveryComponent() {
        for (doc in listOf("docs/THIRD-PARTY.md", "docs/TERCEROS_es.md")) {
            val text = File(root, doc).readText()
            for (c in Licenses.components) {
                val name = c.name.substringBefore(" (").substringBefore(",")
                val shortName = name.removePrefix("LLVM ").substringBefore(" ")
                assertTrue("$doc does not name ${c.name}", name in text || shortName in text)
                assertTrue("$doc does not give ${c.license} for ${c.name}", c.license in text)
                if (c.version.isNotEmpty()) assertTrue("$doc does not give ${c.name} ${c.version}", c.version in text)
            }
        }
    }

    @Test
    fun theLicenceOfTheAppIsTheGpl3() {
        // (el texto de LICENSE, el de la raíz, es el mismo que el que lleva el APK)
        assertTrue(File(root, "LICENSE").readText() == File(folder, "GPL-3.0-or-later.txt").readText())
        assertTrue("GNU GENERAL PUBLIC LICENSE" in File(folder, "GPL-3.0-or-later.txt").readText().take(200))
        assertTrue("Version 3, 29 June 2007" in File(folder, "GPL-3.0-or-later.txt").readText().take(300))
        assertTrue("Version 2, June 1991" in File(folder, "GPL-2.0-or-later.txt").readText().take(300))
    }

    @Test
    fun reflowJoinsTheLinesOfAParagraph() {
        val text = "GNU GENERAL PUBLIC LICENSE\nVersion 3\n\nEveryone is permitted to copy\nand distribute it.\n\n" +
            "  0. Definitions.\n  1. Source Code.\n"
        val out = Licenses.reflow(text)
        assertTrue(out, "Everyone is permitted to copy and distribute it." in out)
        assertTrue(out, "GNU GENERAL PUBLIC LICENSE\nVersion 3" in out)
        assertTrue(out, "0. Definitions.\n1. Source Code." in out)
        // Nada se pierde: las mismas palabras
        assertTrue(text.split(Regex("\\s+")).filter { it.isNotEmpty() } == out.split(Regex("\\s+")).filter { it.isNotEmpty() })
    }
}
