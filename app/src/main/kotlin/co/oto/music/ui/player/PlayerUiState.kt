package co.oto.music.ui.player

import androidx.compose.runtime.Stable
import co.oto.music.playback.PlayerConnection
import co.oto.music.ui.player.models.QueueState
import co.oto.music.ui.player.models.TrackInfo
import co.oto.music.ui.player.models.TransportState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Stable
class PlayerUiState(connection: PlayerConnection) {
    private val scope = connection.scope

    val transportState: StateFlow<TransportState> = combine(
        connection.isEffectivelyPlaying,
        connection.shuffleMode,
        connection.repeatMode,
        connection.canSkipNext,
        connection.canSkipPrevious,
    ) { isPlaying, shuffleMode, repeatMode, canSkipNext, canSkipPrevious ->
        TransportState(
            isPlaying = isPlaying,
            shuffleModeEnabled = shuffleMode != co.oto.music.playback.ShuffleMode.OFF,
            shuffleMode = shuffleMode,
            repeatMode = repeatMode,
            canSkipNext = canSkipNext,
            canSkipPrevious = canSkipPrevious,
        )
    }.combine(connection.playbackState) { partial, playbackState ->
        partial.copy(playbackState = playbackState)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Lazily, TransportState())

    val trackInfo: StateFlow<TrackInfo> = combine(
        connection.mediaMetadata,
        connection.currentSong,
    ) { metadata, song ->
        TrackInfo(
            mediaId = metadata?.id.orEmpty(),
            title = metadata?.title.orEmpty(),
            artist = metadata?.artists?.joinToString { it.name }.orEmpty(),
            album = metadata?.album?.title.orEmpty(),
            thumbnailUrl = metadata?.thumbnailUrl,
            liked = song?.song?.liked == true,
        )
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Lazily, TrackInfo())

    val queueState: StateFlow<QueueState> = combine(
        connection.queueItems,
        connection.currentWindowIndex,
        connection.queueTitle,
    ) { items, currentIndex, title ->
        QueueState(items = items, currentIndex = currentIndex, title = title.orEmpty())
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Lazily, QueueState())

    val progressState = connection.progressState
}
