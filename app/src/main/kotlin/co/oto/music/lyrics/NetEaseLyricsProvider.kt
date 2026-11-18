// NetEaseLyricsProvider.kt

package co.oto.music.lyrics

import android.content.Context
import com.music.directlyrics.NetEase
import co.oto.music.constants.EnableNetEaseKey
import co.oto.music.utils.dataStore
import co.oto.music.utils.get

object NetEaseLyricsProvider : LyricsProvider {
    override val name = "NetEase"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableNetEaseKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = NetEase.getLyrics(title, artist, duration)
}
