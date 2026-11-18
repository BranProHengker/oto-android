// DeezerLyricsProvider.kt

package co.oto.music.lyrics

import android.content.Context
import com.music.directlyrics.Deezer
import co.oto.music.constants.EnableDeezerKey
import co.oto.music.utils.dataStore
import co.oto.music.utils.get

object DeezerLyricsProvider : LyricsProvider {
    override val name = "Deezer"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableDeezerKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Deezer.getLyrics(title, artist, duration)
}
