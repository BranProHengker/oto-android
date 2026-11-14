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
    private val uriCache = java.util.concurrent.ConcurrentHashMap<String, Uri>()

    fun getCachedPlayableUri(uri: Uri): Uri? = uriCache[uri.toString()]

    /**
     * Resolves a local audio URI (which may be a SAF tree document URI with nested paths
     * or non-ASCII characters, a direct document URI, or a MediaStore/file URI) into
     * the most resilient, direct URI for ExoPlayer playback:
     * 1. file:// direct path if readable on disk (fastest, zero IPC, bypasses SAF entirely).
     * 2. Auto-heal renamed files on disk or inside SAF tree parent folder.
     * 3. content://media/external/audio/media/<id> via MediaStore query.
     * 4. Non-tree document URI if SAF tree provider fails isChildDocument checks.
     * 5. Fallback to original URI.
     */
    fun resolvePlayableUri(context: Context, uri: Uri): Uri {
        val uriStr = uri.toString()
        val cached = uriCache[uriStr]
        if (cached != null) return cached

        val scheme = uri.scheme

        // 1. file:// scheme
        if (scheme == "file") {
            val path = uri.path
            if (path != null) {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    uriCache[uriStr] = uri
                    return uri
                }
                val diskMatch = findRenamedFileOnDisk(path)
                if (diskMatch != null) {
                    Timber.tag(TAG).d("Auto-healed file URI: $uri -> $diskMatch")
                    uriCache[uriStr] = diskMatch
                    return diskMatch
                }
            }
            return uri
        }

        // 2. Canonical MediaStore audio URI: test if openable
        if (scheme == "content" && uri.authority == "media") {
            if (testOpen(context, uri)) {
                uriCache[uriStr] = uri
                return uri
            }
        }

        // 3. If original URI can be opened directly without error, use it
        if (testOpen(context, uri)) {
            uriCache[uriStr] = uri
            return uri
        }

        // 4. Try direct POSIX path and check if file exists or was renamed
        val filePath = extractFilePath(uri)
        if (filePath != null) {
            val file = File(filePath)
            val directReadable = runCatching {
                if (file.exists()) {
                    file.canRead() || java.io.FileInputStream(file).use { true }
                } else false
            }.getOrDefault(false)

            if (directReadable) {
                Timber.tag(TAG).d("Resolved SAF URI to direct file: $filePath")
                val directUri = Uri.fromFile(file)
                uriCache[uriStr] = directUri
                return directUri
            }

            // Check if renamed on disk in the same directory
            val renamedDisk = findRenamedFileOnDisk(filePath)
            if (renamedDisk != null) {
                Timber.tag(TAG).d("Auto-healed renamed file on disk: $renamedDisk")
                uriCache[uriStr] = renamedDisk
                return renamedDisk
            }

            // Query MediaStore by path
            val mediaStoreUri = queryMediaStore(context, file)
            if (mediaStoreUri != null && testOpen(context, mediaStoreUri)) {
                Timber.tag(TAG).d("Resolved file path to MediaStore URI: $mediaStoreUri")
                uriCache[uriStr] = mediaStoreUri
                return mediaStoreUri
            }
        }

        // 5. SAF Tree Resolution & Renamed Search
        val docId = extractDocId(uri)
        if (docId != null) {
            val decodedDocId = runCatching { URLDecoder.decode(docId, "UTF-8") }.getOrDefault(docId)

            if (uriStr.contains("/tree/")) {
                val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                    ?: uriStr.substringAfter("/tree/").substringBefore("/document/")
                        .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                val treeUri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", treeId)

                if (decodedDocId.contains('/')) {
                    val parentDocId = decodedDocId.substringBeforeLast('/')
                    val fileName = decodedDocId.substringAfterLast('/')
                    val safHealed = findRenamedFileInSaf(context, treeUri, parentDocId, fileName)
                    if (safHealed != null && testOpen(context, safHealed)) {
                        Timber.tag(TAG).d("Auto-healed SAF tree URI to: $safHealed")
                        uriCache[uriStr] = safHealed
                        return safHealed
                    }
                }

                // Check saved user folders from LocalFolders
                val folders = com.mochyy.mochymusic.importer.LocalFolders.read(context)
                for (savedTree in folders) {
                    if (decodedDocId.contains('/')) {
                        val parentDocId = decodedDocId.substringBeforeLast('/')
                        val fileName = decodedDocId.substringAfterLast('/')
                        val safHealed = findRenamedFileInSaf(context, savedTree, parentDocId, fileName)
                        if (safHealed != null && testOpen(context, safHealed)) {
                            Timber.tag(TAG).d("Auto-healed via saved LocalFolder: $safHealed")
                            uriCache[uriStr] = safHealed
                            return safHealed
                        }
                    }
                }
            }

            // 6. MediaStore query by displayName prefix/fuzzy
            val fileName = decodedDocId.substringAfterLast('/')
            val msMatch = findRenamedFileInMediaStore(context, fileName)
            if (msMatch != null && testOpen(context, msMatch)) {
                Timber.tag(TAG).d("Auto-healed via MediaStore displayName match: $msMatch")
                uriCache[uriStr] = msMatch
                return msMatch
            }

            val msByDoc = queryMediaStoreByDocId(context, docId)
            if (msByDoc != null && testOpen(context, msByDoc)) {
                Timber.tag(TAG).d("Resolved docId to MediaStore URI: $msByDoc")
                uriCache[uriStr] = msByDoc
                return msByDoc
            }

            // 7. Non-tree document URI fallback
            if (uriStr.contains("/tree/")) {
                val nonTreeUri = runCatching {
                    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
                }.getOrNull()
                if (nonTreeUri != null && testOpen(context, nonTreeUri)) {
                    Timber.tag(TAG).d("Resolved tree URI to non-tree document URI: $nonTreeUri")
                    uriCache[uriStr] = nonTreeUri
                    return nonTreeUri
                }
            }
        }

        // 8. MediaStore.getMediaUri fallback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val msUri = runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
            if (msUri != null && testOpen(context, msUri)) {
                Timber.tag(TAG).d("Resolved via MediaStore.getMediaUri: $msUri")
                uriCache[uriStr] = msUri
                return msUri
            }
        }

        // Do not cache broken URIs
        return uri
    }

    fun extractDocId(uri: Uri): String? {
        val str = uri.toString()
        if (str.contains("/document/")) {
            val raw = str.substringAfter("/document/").substringBefore('?')
            val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()
            if (!decoded.isNullOrBlank()) return decoded
        }
        return runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
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

    private fun findRenamedFileInSaf(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        oldFileName: String,
    ): Uri? {
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        }.getOrNull() ?: return null

        val oldBase = oldFileName.substringBeforeLast('.')
        val oldExt = oldFileName.substringAfterLast('.', "")

        return runCatching {
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                var candidateDocId: String? = null
                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(0) ?: continue
                    val displayName = cursor.getString(1) ?: continue
                    val childExt = displayName.substringAfterLast('.', "")
                    if (oldExt.isNotBlank() && !childExt.equals(oldExt, ignoreCase = true)) continue

                    val childBase = displayName.substringBeforeLast('.')
                    if (childBase.equals(oldBase, ignoreCase = true)) {
                        return@use DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                    }

                    if (isTitleMatch(oldBase, childBase)) {
                        candidateDocId = childDocId
                    }
                }
                candidateDocId?.let { DocumentsContract.buildDocumentUriUsingTree(treeUri, it) }
            }
        }.getOrNull()
    }

    private fun findRenamedFileOnDisk(filePath: String): Uri? {
        val file = File(filePath)
        val parent = file.parentFile ?: return null
        if (!parent.exists() || !parent.isDirectory) return null

        val oldBase = file.nameWithoutExtension
        val oldExt = file.extension

        return runCatching {
            val candidates = parent.listFiles() ?: return@runCatching null
            var bestMatch: File? = null
            for (candidate in candidates) {
                if (!candidate.isFile) continue
                if (oldExt.isNotBlank() && !candidate.extension.equals(oldExt, ignoreCase = true)) continue

                val candidateBase = candidate.nameWithoutExtension
                if (candidateBase.equals(oldBase, ignoreCase = true)) {
                    bestMatch = candidate
                    break
                }
                if (isTitleMatch(oldBase, candidateBase)) {
                    bestMatch = candidate
                }
            }
            bestMatch?.takeIf { it.canRead() }?.let { Uri.fromFile(it) }
        }.getOrNull()
    }

    private fun isTitleMatch(oldBase: String, candidateBase: String): Boolean {
        val c = candidateBase.trim()
        val t = oldBase.trim()
        if (c.equals(t, ignoreCase = true)) return true
        if (c.startsWith(t, ignoreCase = true) || t.startsWith(c, ignoreCase = true)) return true

        val candClean = stripSuffix(c)
        val targetClean = stripSuffix(t)
        if (candClean.equals(targetClean, ignoreCase = true) ||
            candClean.equals(t, ignoreCase = true) ||
            c.equals(targetClean, ignoreCase = true)
        ) return true

        // Compare bare song title without artist prefix ("Artist - Title" -> "Title")
        val candTitle = if (candClean.contains(" - ")) candClean.substringAfterLast(" - ").trim() else candClean
        val targetTitle = if (targetClean.contains(" - ")) targetClean.substringAfterLast(" - ").trim() else targetClean
        if (candTitle.isNotEmpty() && targetTitle.isNotEmpty() && candTitle.equals(targetTitle, ignoreCase = true)) {
            return true
        }

        return false
    }

    private fun stripSuffix(name: String): String {
        return if (name.contains(" - ")) name.substringBeforeLast(" - ").trim() else name.trim()
    }

    private fun findRenamedFileInMediaStore(context: Context, oldFileName: String): Uri? {
        val oldBase = oldFileName.substringBeforeLast('.')
        val oldExt = oldFileName.substringAfterLast('.', "")
        val externalUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)

        val cleanPrefix = stripSuffix(oldBase)
        val queries = listOf("$oldBase%", "$cleanPrefix%")
        for (q in queries.distinct()) {
            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf(q)

            val match = runCatching {
                context.contentResolver.query(externalUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)) ?: continue
                        val ext = name.substringAfterLast('.', "")
                        if (oldExt.isNotBlank() && !ext.equals(oldExt, ignoreCase = true)) continue
                        if (isTitleMatch(oldBase, name.substringBeforeLast('.'))) {
                            return@use ContentUris.withAppendedId(externalUri, id)
                        }
                    }
                    null
                }
            }.getOrNull()
            if (match != null) return match
        }
        return null
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
