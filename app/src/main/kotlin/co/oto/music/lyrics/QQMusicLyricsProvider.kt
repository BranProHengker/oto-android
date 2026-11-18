// QQMusicLyricsProvider.kt

package co.oto.music.lyrics

import android.content.Context
import com.music.directlyrics.QQMusic
import co.oto.music.constants.EnableQQMusicKey
import co.oto.music.utils.dataStore
import co.oto.music.utils.get

object QQMusicLyricsProvider : LyricsProvider {
    override val name = "QQMusic"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableQQMusicKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = QQMusic.getLyrics(title, artist, duration)
}
