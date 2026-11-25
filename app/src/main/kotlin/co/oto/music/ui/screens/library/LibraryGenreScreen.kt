// LibraryGenreScreen.kt
//
// One genre from the Library's Genres tab, as a playlist: every song tagged with it or by an artist
// of that genre, a to z, with Play, shuffle and search.

package co.oto.music.ui.screens.library

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import co.oto.music.LocalPlayerAwareWindowInsets
import co.oto.music.LocalPlayerConnection
import co.oto.music.R
import co.oto.music.db.entities.Song
import co.oto.music.extensions.toMediaItem
import co.oto.music.playback.queues.ListQueue
import co.oto.music.ui.component.LocalMenuState
import co.oto.music.ui.component.SongSwipeBox
import co.oto.music.ui.component.detail.PlaylistEndOfLine
import co.oto.music.ui.component.detail.PlaylistTrackBones
import co.oto.music.ui.component.detail.PlaylistTrackRow
import co.oto.music.ui.component.librarySwipeAction
import co.oto.music.ui.component.queueSwipeAction
import co.oto.music.ui.menu.SongMenu
import co.oto.music.ui.screens.playlist.GeneratedNoResults
import co.oto.music.ui.screens.playlist.GeneratedPlaylistPage
import co.oto.music.ui.utils.backToMain
import co.oto.music.utils.makeTimeString
import co.oto.music.viewmodels.LocalGenreViewModel

@Composable
fun LibraryGenreScreen(navController: NavController) {
    val viewModel: LocalGenreViewModel = hiltViewModel()
    val loaded by viewModel.songs.collectAsState()
    val songs = loaded.orEmpty()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    // states, read in the rows only, so a play or pause doesn't recompose the page
    val nowPlaying = playerConnection.mediaMetadata.collectAsState()
    val isPlaying = playerConnection.isEffectivelyPlaying.collectAsState()
    val currentQueue by playerConnection.queueTitle.collectAsState()

    val title = viewModel.name
    var query by remember { mutableStateOf(TextFieldValue()) }
    val listState = rememberLazyListState()
    val totalSeconds = remember(songs) { songs.sumOf { it.song.duration.coerceAtLeast(0) } }
    val shown = remember(songs, query.text) {
        val q = query.text.trim()
        if (q.isEmpty()) songs else songs.filter { song -> song.song.title.contains(q, true) || song.artists.any { it.name.contains(q, true) } }
    }
    val isThisQueue = currentQueue == title

    fun play(start: Song?, shuffle: Boolean) {
        if (songs.isEmpty()) return
        val ordered = if (shuffle) songs.shuffled() else songs
        playerConnection.playQueue(
            ListQueue(
                title = title,
                items = ordered.map { it.toMediaItem() },
                startIndex = start?.let { ordered.indexOf(it).coerceAtLeast(0) } ?: 0,
            ),
        )
    }

    GeneratedPlaylistPage(
        title = title,
        coverUrl = songs.firstOrNull { !it.song.thumbnailUrl.isNullOrEmpty() }?.song?.thumbnailUrl,
        stats = stringResource(
            R.string.lib_stats_in_library_time,
            pluralStringResource(R.plurals.n_song, songs.size, songs.size),
            listeningTime(totalSeconds),
        ),
        lazyListState = listState,
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)
            .union(WindowInsets.ime).asPaddingValues(),
        isPlaying = isThisQueue && isPlaying.value,
        onPlayClick = { if (isThisQueue) playerConnection.togglePlayPause() else play(null, shuffle = false) },
        onShuffleClick = { play(null, shuffle = true) },
        query = query,
        onQueryChange = { query = it },
        searchPlaceholder = stringResource(R.string.lib_genre_search),
        onBackClick = { navController.navigateUp() },
        onBackLongClick = { navController.backToMain() },
    ) {
        if (loaded == null) {
            item(key = "loading", contentType = "loading") { PlaylistTrackBones() }
            return@GeneratedPlaylistPage
        }
        if (shown.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                if (query.text.isNotBlank()) GeneratedNoResults(query.text) else LibraryEmptyState()
            }
        }
        items(shown, key = { it.id }, contentType = { "track" }) { song ->
            SongSwipeBox(
                library = { librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                val active = song.id == nowPlaying.value?.id
                PlaylistTrackRow(
                    thumbnailUrl = song.song.thumbnailUrl,
                    songId = song.id,
                    title = song.song.title,
                    subtitle = listOfNotNull(
                        song.artists.joinToString { it.name }.ifBlank { null },
                        song.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
                    ).joinToString(" • "),
                    isActive = active,
                    isPlaying = active && isPlaying.value,
                    onClick = { if (active) playerConnection.togglePlayPause() else play(song, shuffle = false) },
                    onLongClick = {
                        menuState.show { SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss) }
                    },
                    onMenuClick = {
                        menuState.show { SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss) }
                    },
                )
            }
        }
        if (query.text.isBlank() && songs.isNotEmpty()) {
            item(key = "end_of_line", contentType = "end") { PlaylistEndOfLine(songCount = songs.size, totalSeconds = totalSeconds) }
        }
        item(key = "bottom_spacer", contentType = "spacer") { Spacer(Modifier.height(50.dp)) }
    }
}
