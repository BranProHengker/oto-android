// SidecarLyrics.kt
//
// Reads synchronized or plain .lrc files located in the same directory as a local audio file.

package com.mochyy.mochymusic.lyrics

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.mochyy.mochymusic.importer.LocalFolders
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

object SidecarLyrics {
    const val Provider = "LocalLrc"

    /**
     * Looks for a .lrc file in the same folder as [uri].
     * Checks direct file paths, MediaStore.Files, and SAF document trees.
     */
    fun find(context: Context, uri: Uri): String? {
        return when (uri.scheme) {
            "file" -> uri.path?.let { findForPath(it) }
            "content" -> findForContentUri(context, uri)
            else -> null
        }
    }

    private fun findForPath(path: String): String? {
        val audioFile = File(path)
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension

        // 1. Direct match: <baseName>.lrc
        val lrc = File(parent, "$baseName.lrc")
        if (lrc.exists() && lrc.canRead()) {
            return readLrcFile(lrc)
        }

        // 2. Direct match: <baseName>.LRC
        val lrcUpper = File(parent, "$baseName.LRC")
        if (lrcUpper.exists() && lrcUpper.canRead()) {
            return readLrcFile(lrcUpper)
        }

        // 3. Case-insensitive match among siblings in the same directory
        return runCatching {
            parent.listFiles()?.firstOrNull { file ->
                file.isFile &&
                    file.extension.equals("lrc", ignoreCase = true) &&
                    file.nameWithoutExtension.equals(baseName, ignoreCase = true)
            }?.takeIf { it.canRead() }?.let { readLrcFile(it) }
        }.getOrNull()
    }

    private fun findForContentUri(context: Context, uri: Uri): String? {
        var directPath: String? = null
        var displayName: String? = null

        runCatching {
            val projection = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIdx != -1) directPath = cursor.getString(dataIdx)
                    val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameIdx != -1) displayName = cursor.getString(nameIdx)
                }
            }
        }

        if (!directPath.isNullOrBlank()) {
            findForPath(directPath)?.let { return it }

            // If direct File read failed (e.g. Scoped Storage restrictions),
            // query MediaStore.Files using the expected LRC path
            runCatching {
                val audioFile = File(directPath)
                val parent = audioFile.parentFile
                if (parent != null) {
                    val baseName = audioFile.nameWithoutExtension
                    val targetPath = File(parent, "$baseName.lrc").absolutePath
                    val targetPathUpper = File(parent, "$baseName.LRC").absolutePath
                    val filesUri = MediaStore.Files.getContentUri("external")
                    context.contentResolver.query(
                        filesUri,
                        arrayOf(MediaStore.Files.FileColumns._ID),
                        "${MediaStore.Files.FileColumns.DATA} = ? OR ${MediaStore.Files.FileColumns.DATA} = ?",
                        arrayOf(targetPath, targetPathUpper),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(0)
                            val lrcUri = ContentUris.withAppendedId(filesUri, id)
                            readFromUri(context, lrcUri)?.let { return it }
                        }
                    }
                }
            }
        }

        // Try SAF Document Tree (if the song is from a folder added via LocalFolders)
        if (DocumentsContract.isDocumentUri(context, uri)) {
            runCatching {
                val docId = DocumentsContract.getDocumentId(uri)
                val baseDocId = docId.substringBeforeLast('.')
                val folders = LocalFolders.read(context)
                for (treeUri in folders) {
                    val candidateId = "$baseDocId.lrc"
                    val candidateUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, candidateId)
                    readFromUri(context, candidateUri)?.let { return it }

                    val candidateIdUpper = "$baseDocId.LRC"
                    val candidateUriUpper = DocumentsContract.buildDocumentUriUsingTree(treeUri, candidateIdUpper)
                    readFromUri(context, candidateUriUpper)?.let { return it }
                }
            }
        }

        // Fallback: Query MediaStore.Files by display name
        if (!displayName.isNullOrBlank()) {
            runCatching {
                val baseName = displayName.substringBeforeLast('.')
                val filesUri = MediaStore.Files.getContentUri("external")
                context.contentResolver.query(
                    filesUri,
                    arrayOf(MediaStore.Files.FileColumns._ID),
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?",
                    arrayOf("$baseName.lrc", "$baseName.LRC"),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val lrcUri = ContentUris.withAppendedId(filesUri, id)
                        readFromUri(context, lrcUri)?.let { return it }
                    }
                }
            }
        }

        return null
    }

    private fun readLrcFile(file: File): String? = runCatching {
        file.inputStream().use { readLrcStream(it) }
    }.getOrNull()

    fun readFromUri(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { readLrcStream(it) }
    }.getOrNull()

    private fun readLrcStream(stream: InputStream): String? = runCatching {
        val bytes = stream.readBytes()
        if (bytes.isEmpty()) return null
        decodeBytes(bytes).takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun decodeBytes(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (!utf8.contains('\uFFFD')) utf8 else String(bytes, Charset.defaultCharset())
    }
}
