package com.mochyy.mochymusic.playback

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import timber.log.Timber
import java.io.File
import java.net.URLDecoder

object LocalAudioResolver {

    private const val TAG = "LocalAudioResolver"

    /**
     * Resolves a local audio URI (which may be a SAF tree document URI with nested paths
     * or non-ASCII characters, a direct document URI, or a MediaStore/file URI) into
     * the most resilient, direct URI for ExoPlayer playback:
     * 1. file:// direct path if readable on disk (fastest, zero IPC, bypasses SAF entirely).
     * 2. content://media/external/audio/media/<id> via MediaStore query.
     * 3. Non-tree document URI if SAF tree provider fails isChildDocument checks.
     * 4. Fallback to original URI.
     */
    fun resolvePlayableUri(context: Context, uri: Uri): Uri {
        val scheme = uri.scheme
        if (scheme == "file") {
            return uri
        }

        // Already a canonical MediaStore audio URI
        if (scheme == "content" && uri.authority == "media") {
            return uri
        }

        // Try resolving to direct file path first
        val filePath = extractFilePath(uri)
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists() && file.canRead()) {
                Timber.tag(TAG).d("Resolved SAF URI to direct file: $filePath")
                return Uri.fromFile(file)
            }

            // If direct file is blocked by Scoped Storage, query MediaStore
            val mediaStoreUri = queryMediaStore(context, file)
            if (mediaStoreUri != null) {
                Timber.tag(TAG).d("Resolved file path to MediaStore URI: $mediaStoreUri")
                return mediaStoreUri
            }
        }

        // Try extracting document ID and resolving via MediaStore or non-tree SAF
        val docId = extractDocId(uri)
        if (docId != null) {
            val mediaStoreUri = queryMediaStoreByDocId(context, docId)
            if (mediaStoreUri != null) {
                Timber.tag(TAG).d("Resolved docId to MediaStore URI: $mediaStoreUri")
                return mediaStoreUri
            }

            // If it's a tree document URI, try non-tree document URI to bypass isChildDocument checks
            val uriStr = uri.toString()
            if (uriStr.contains("/tree/")) {
                val nonTreeUri = runCatching {
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
                }.getOrNull()
                if (nonTreeUri != null && testOpen(context, nonTreeUri)) {
                    Timber.tag(TAG).d("Resolved tree URI to non-tree document URI: $nonTreeUri")
                    return nonTreeUri
                }
            }
        }

        return uri
    }

    fun extractDocId(uri: Uri): String? {
        return runCatching {
            if (DocumentsContract.isDocumentUri(null, uri) || uri.toString().contains("/document/")) {
                DocumentsContract.getDocumentId(uri)
            } else {
                null
            }
        }.getOrNull() ?: run {
            val str = uri.toString()
            if (str.contains("/document/")) {
                val raw = str.substringAfter("/document/").substringBefore('?')
                runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()
            } else null
        }
    }

    fun extractFilePath(uri: Uri): String? {
        val scheme = uri.scheme
        if (scheme == "file") return uri.path

        val docId = extractDocId(uri) ?: return null
        val decodedDocId = runCatching { URLDecoder.decode(docId, "UTF-8") }.getOrDefault(docId)

        return when {
            decodedDocId.startsWith("primary:") -> {
                val subPath = decodedDocId.removePrefix("primary:")
                val externalStorage = Environment.getExternalStorageDirectory().absolutePath
                "$externalStorage/$subPath"
            }
            decodedDocId.contains(":") -> {
                val volume = decodedDocId.substringBefore(":")
                val subPath = decodedDocId.substringAfter(":")
                "/storage/$volume/$subPath"
            }
            decodedDocId.startsWith("/") -> decodedDocId
            else -> null
        }
    }

    fun queryMediaStore(context: Context, file: File): Uri? {
        val cr = context.contentResolver
        val externalUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val path = file.absolutePath

        // 1. Query by DATA column (absolute path)
        try {
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.DATA} = ?"
            val selectionArgs = arrayOf(path)
            cr.query(externalUri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    return ContentUris.withAppendedId(externalUri, id)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "MediaStore query by DATA failed")
        }

        // 2. Query by DISPLAY_NAME and RELATIVE_PATH
        try {
            val fileName = file.name
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val extPath = Environment.getExternalStorageDirectory().absolutePath
                val relPath = file.parentFile?.absolutePath
                    ?.removePrefix(extPath)
                    ?.trimStart('/')
                    ?.let { if (!it.endsWith("/")) "$it/" else it }

                if (!relPath.isNullOrEmpty()) {
                    val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
                    val selectionArgs = arrayOf(fileName, relPath)
                    cr.query(externalUri, projection, selection, selectionArgs, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                            return ContentUris.withAppendedId(externalUri, id)
                        }
                    }
                }
            }

            // Fallback: match by DISPLAY_NAME and SIZE
            if (file.exists()) {
                val size = file.length()
                val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.SIZE} = ?"
                val selectionArgs = arrayOf(fileName, size.toString())
                cr.query(externalUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        return ContentUris.withAppendedId(externalUri, id)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "MediaStore query by name/size failed")
        }

        return null
    }

    private fun queryMediaStoreByDocId(context: Context, docId: String): Uri? {
        val decoded = runCatching { URLDecoder.decode(docId, "UTF-8") }.getOrDefault(docId)
        val fileName = decoded.substringAfterLast('/')
        if (fileName.isBlank()) return null

        try {
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(fileName)
            context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "MediaStore query by docId displayName failed")
        }
        return null
    }

    private fun testOpen(context: Context, uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }
}
