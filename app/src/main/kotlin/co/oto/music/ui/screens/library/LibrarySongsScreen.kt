// LibrarySongsScreen.kt

package co.oto.music.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import co.oto.music.LocalPlayerConnection
import co.oto.music.R
import co.oto.music.extensions.toMediaItem
import co.oto.music.playback.queues.ListQueue
import co.oto.music.ui.component.LocalMenuState
import co.oto.music.ui.component.SongSwipeBox
import co.oto.music.ui.component.librarySwipeAction
import co.oto.music.ui.component.queueSwipeAction
import co.oto.music.ui.menu.SongMenu
import co.oto.music.viewmodels.LibraryHomeViewModel

@Composable
fun LibrarySongsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val songs by viewModel.songs.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val songsTitle = stringResource(R.string.songs)

    LibraryEntityListScreen(
        title = songsTitle,
        subtitle = stringResource(R.string.lib_songs_subtitle),
        searchPlaceholder = stringResource(R.string.lib_songs_search),
        items = songs,
        idOf = { it.id },
        nameOf = { it.song.title },
        subtitleOf = { it.artists.joinToString { a -> a.name }.ifBlank { null } },
        thumbnailOf = { it.song.thumbnailUrl },
        playingIdOf = { it.id },
        onClick = { song ->
            // Queue the alphabetical order the user is looking at, not the underlying
            // create-date order — otherwise tapping a row starts a queue that jumps somewhere
            // unrelated to what's on screen.
            val ordered = sortForLibraryIndex(songs) { it.song.title }
            playerConnection.playQueue(
                ListQueue(
                    title = songsTitle,
                    items = ordered.map { it.toMediaItem() },
                    startIndex = ordered.indexOf(song).coerceAtLeast(0),
                ),
            )
        },
        onLongClick = { song ->
            menuState.show {
                SongMenu(
                    originalSong = song,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
        // left queues the song, right takes it out of the library (it's already in it)
        rowWrapper = { song, row ->
            SongSwipeBox(
                slab = false,
                library = { librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                row()
            }
        },
    )
}
