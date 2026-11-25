// SPDX-License-Identifier: GPL-3.0-or-later
/*
 * Una carpeta elegida por el usuario (el selector de carpetas de Android), como
 * lugar de los .slx: por ejemplo la de la música, de modo que cada .slx quede
 * junto a su grabación y viaje con ella, como en py-slowdown (con Syncthing,
 * Dropbox o un cable). Se lee y se escribe con DocumentsContract, sin AndroidX.
 */
package io.github.nivaca.slowdown

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

class TreeFolder(private val resolver: ContentResolver, private val tree: Uri) : SlxFolder {
    private val root = DocumentsContract.getTreeDocumentId(tree)

    // El nombre de cada documento de la carpeta (no de sus subcarpetas) y su identificador
    private fun children(): Map<String, String> {
        val out = HashMap<String, String>()
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, root)
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                out[name] = id
            }
        }
        return out
    }

    private fun document(name: String): Uri? =
        children()[name]?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it) }

    override fun read(name: String): String? = try {
        document(name)?.let { uri -> resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }
    } catch (_: Exception) {
        null
    }

    override fun write(name: String, text: String) {
        val uri = document(name) ?: DocumentsContract.createDocument(resolver,
            DocumentsContract.buildDocumentUriUsingTree(tree, root), MIME, name)
            ?: throw java.io.IOException("cannot create $name")
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw java.io.IOException("cannot write $name")
    }

    override fun exists(name: String) = try { name in children() } catch (_: Exception) { false }

    /** The name of the folder, as Android shows it. */
    fun label(): String = try {
        resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, root),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: tree.lastPathSegment ?: ""
    } catch (_: Exception) {
        tree.lastPathSegment ?: ""
    }

    companion object {
        // (con este tipo, Android no le añade otra extensión al nombre)
        const val MIME = "application/octet-stream"
    }
}
