// LrcRedLyricsProvider.kt

package co.oto.music.lyrics

import android.content.Context
import com.music.directlyrics.LrcRed
import co.oto.music.betterlyrics.TTMLParser
import co.oto.music.constants.EnableLrcRedKey
import co.oto.music.utils.dataStore
import co.oto.music.utils.get

object LrcRedLyricsProvider : LyricsProvider {
    override val name = "LrcRed"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcRedKey] ?: true

    // lrc.red speaks the same ttml as apple music, so the better lyrics parser keeps the word timing, singers and background vocals
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = LrcRed.getTtml(title, artist, duration).mapCatching { ttml ->
        val lines = TTMLParser.parseTTML(ttml)
        check(lines.isNotEmpty()) { "lrc.red has no timed lyrics for this track" }
        TTMLParser.toLRC(lines)
    }
}
