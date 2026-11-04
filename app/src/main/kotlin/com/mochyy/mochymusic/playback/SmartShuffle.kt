package com.mochyy.mochymusic.playback

import androidx.media3.common.MediaItem
import com.mochyy.mochymusic.extensions.metadata
import java.io.File
import kotlin.math.abs
import kotlin.random.Random

object SmartShuffle {

    fun smartScramble(
        candidates: List<MediaItem>,
        sourceList: List<MediaItem>,
        currentTrack: MediaItem?,
    ): List<MediaItem> {
        val pool = candidates.ifEmpty { prepareCandidatesPool(sourceList, currentTrack) }
        if (pool.isEmpty()) return emptyList()
        if (currentTrack == null) return pool.shuffled()

        val curTags = extractTags(currentTrack)
        val curArtists = extractArtists(currentTrack)
        val curAlbum = currentTrack.mediaMetadata.albumTitle?.toString()?.trim()?.lowercase().orEmpty()
        val (curFolder, curParent) = extractFolderAndParent(currentTrack)
        val curDurationSec = extractDurationSec(currentTrack)

        val scored = pool.map { candidate ->
            var weight = 1.0

            // 1. Tag / Genre match (+15.0 per matching tag/genre)
            if (curTags.isNotEmpty()) {
                val candTags = extractTags(candidate)
                for (tag in candTags) {
                    if (tag in curTags) {
                        weight += 15.0
                    }
                }
            }

            // 2. Artist match (+12.0 if sharing artist or collaborator)
            if (curArtists.isNotEmpty()) {
                val candArtists = extractArtists(candidate)
                if (curArtists.any { it in candArtists }) {
                    weight += 12.0
                }
            }

            // 3. Album match (+8.0 if from same album)
            if (curAlbum.isNotEmpty()) {
                val candAlbum = candidate.mediaMetadata.albumTitle?.toString()?.trim()?.lowercase().orEmpty()
                if (curAlbum == candAlbum) {
                    weight += 8.0
                }
            }

            // 4. Folder Proximity for local tracks (+10.0 same folder, +5.0 parent folder)
            if (curFolder != null) {
                val (candFolder, candParent) = extractFolderAndParent(candidate)
                if (candFolder != null) {
                    if (curFolder == candFolder) {
                        weight += 10.0
                    } else if (curParent != null && curParent == candParent) {
                        weight += 5.0
                    }
                }
            }

            // 5. Duration Proximity within 35 seconds (+3.0)
            if (curDurationSec > 0.0) {
                val candDurationSec = extractDurationSec(candidate)
                if (candDurationSec > 0.0 && abs(candDurationSec - curDurationSec) < 35.0) {
                    weight += 3.0
                }
            }

            // Efraimidis-Spirakis weighted random key: r^(1/weight)
            val r = Random.nextDouble().coerceIn(1e-6, 1.0 - 1e-6)
            val key = Math.pow(r, 1.0 / weight)
            Pair(key, candidate)
        }

        return scored.sortedByDescending { it.first }.map { it.second }
    }

    fun scramble(
        candidates: List<MediaItem>,
        sourceList: List<MediaItem>,
        currentTrack: MediaItem?,
    ): List<MediaItem> {
        val pool = candidates.ifEmpty { prepareCandidatesPool(sourceList, currentTrack) }.toMutableList()
        pool.shuffle()
        return pool
    }

    fun restore(
        candidates: List<MediaItem>,
        sourceList: List<MediaItem>,
        currentTrack: MediaItem?,
    ): List<MediaItem> {
        val knownSourceIds = sourceList.map { it.mediaId }.toSet()
        val extra = candidates.filter { it.mediaId !in knownSourceIds }

        val currentId = currentTrack?.mediaId
        val at = if (currentId != null) sourceList.indexOfFirst { it.mediaId == currentId } else -1
        val tail = when {
            at >= 0 && at + 1 < sourceList.size -> sourceList.subList(at + 1, sourceList.size)
            at >= 0 -> emptyList()
            else -> sourceList
        }

        return tail + extra
    }

    private fun prepareCandidatesPool(
        sourceList: List<MediaItem>,
        currentTrack: MediaItem?,
    ): List<MediaItem> {
        val currentId = currentTrack?.mediaId
        return if (currentId != null) {
            sourceList.filter { it.mediaId != currentId }
        } else {
            sourceList
        }
    }

    private fun extractTags(item: MediaItem): Set<String> {
        val tags = mutableSetOf<String>()
        val genre = item.mediaMetadata.genre?.toString()
        if (!genre.isNullOrBlank()) {
            genre.split(',', '/', ';').forEach {
                val t = it.trim().lowercase()
                if (t.isNotEmpty()) tags += t
            }
        }
        item.mediaMetadata.extras?.getStringArrayList("tags")?.forEach {
            val t = it.trim().lowercase()
            if (t.isNotEmpty()) tags += t
        }
        return tags
    }

    private fun extractArtists(item: MediaItem): Set<String> {
        val artists = mutableSetOf<String>()
        item.metadata?.artists?.forEach {
            val a = it.name.trim().lowercase()
            if (a.isNotEmpty()) artists += a
        }
        val artistStr = item.mediaMetadata.artist?.toString() ?: item.mediaMetadata.subtitle?.toString()
        if (!artistStr.isNullOrBlank()) {
            artistStr.split(',', '&', "feat.", "ft.", "/").forEach {
                val a = it.trim().lowercase()
                if (a.isNotEmpty()) artists += a
            }
        }
        return artists
    }

    private fun extractFolderAndParent(item: MediaItem): Pair<String?, String?> {
        val uriStr = item.localConfiguration?.uri?.toString()
            ?: item.metadata?.localUri
            ?: item.mediaMetadata.extras?.getString("local_uri")
        if (uriStr.isNullOrEmpty()) return Pair(null, null)

        val cleanPath = when {
            uriStr.startsWith("file://") -> uriStr.removePrefix("file://")
            uriStr.startsWith("/") -> uriStr
            else -> item.localConfiguration?.uri?.path ?: uriStr
        }

        val file = File(cleanPath)
        val folder = file.parent
        val parentFolder = file.parentFile?.parent
        return Pair(folder, parentFolder)
    }

    private fun extractDurationSec(item: MediaItem): Double {
        val sec = item.metadata?.duration ?: 0
        if (sec > 0) return sec.toDouble()
        val ms = item.mediaMetadata.extras?.getLong("duration_ms", 0L) ?: 0L
        if (ms > 0L) return ms / 1000.0
        return 0.0
    }
}
