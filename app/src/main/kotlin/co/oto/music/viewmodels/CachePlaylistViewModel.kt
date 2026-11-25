// CachePlaylistViewModel.kt

package co.oto.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.SimpleCache
import co.oto.music.constants.HideExplicitKey
import co.oto.music.constants.HideVideoSongsKey
import co.oto.music.db.MusicDatabase
import co.oto.music.db.entities.Song
import co.oto.music.di.DownloadCache
import co.oto.music.di.PlayerCache
import co.oto.music.extensions.filterExplicit
import co.oto.music.extensions.filterVideoSongs
import co.oto.music.utils.dataStore
import co.oto.music.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class CachePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    @PlayerCache private val playerCache: SimpleCache,
    @DownloadCache private val downloadCache: SimpleCache
) : ViewModel() {

    // polls only while the cache playlist screen is visible and off the main thread
    val cachedSongs: StateFlow<List<Song>> = flow {
        while (true) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val cachedIds = playerCache.keys.toSet()
            val downloadedIds = downloadCache.keys.toSet()
            val pureCacheIds = cachedIds.subtract(downloadedIds)

            val songs = if (pureCacheIds.isNotEmpty()) {
                database.getSongsByIds(pureCacheIds.toList())
            } else {
                emptyList()
            }

            val completeSongs = songs.filter {
                val contentLength = it.format?.contentLength
                contentLength != null && playerCache.isCached(it.song.id, 0, contentLength)
            }

            if (completeSongs.isNotEmpty()) {
                database.query {
                    completeSongs.forEach {
                        if (it.song.dateDownload == null) {
                            update(it.song.copy(dateDownload = LocalDateTime.now()))
                        }
                    }
                }
            }

            emit(
                completeSongs
                    .filter { it.song.dateDownload != null }
                    .sortedByDescending { it.song.dateDownload }
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs),
            )

            delay(3000)
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeSongFromCache(songId: String) {
        playerCache.removeResource(songId)
    }
}
