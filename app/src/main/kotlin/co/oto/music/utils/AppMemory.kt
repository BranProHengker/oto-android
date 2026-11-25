package co.oto.music.utils

import android.content.ComponentCallbacks2
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import coil3.SingletonImageLoader
import co.oto.music.lyrics.LyricsUtils
import co.oto.music.ui.component.BlurEffectCache
import co.oto.music.ui.player.video.YouTubeVideoSessions
import co.oto.music.utils.cipher.CipherDeobfuscator
import co.oto.music.utils.sabr.EjsNTransformSolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// frees caches and ui-only engines when the screen hides or the system runs short, leaving playback and dac output untouched
object AppMemory {
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(Int) -> Unit>()

    // lets services and ui layers drop their own idle buffers on memory trims without coupling them to App
    fun register(listener: (Int) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    @Suppress("DEPRECATION")
    fun onTrimMemory(context: Context, level: Int, scope: CoroutineScope) {
        val imageCache = SingletonImageLoader.get(context).memoryCache
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                imageCache?.clear()
                BlurEffectCache.clear()
                YouTubeVideoSessions.releaseIdle()
                LyricsUtils.releaseKuromoji()
                ArtistPageCache.clear()
                SongVersions.trimMemory()
                co.oto.music.ui.player.info.SongAboutLoader.trimMemory()
                co.oto.music.canvas.SpotifyCanvasProvider.trimMemory()
                PlaybackLogManager.trimMemory()
                scope.launch(Dispatchers.Main) {
                    YTPlayerUtils.releaseWebViews()
                    CipherDeobfuscator.releaseWebView()
                    EjsNTransformSolver.close()
                }
                scope.launch(Dispatchers.IO) {
                    // closing idle tls sockets writes close_notify; keep socket/http teardown off the main thread
                    runCatching { YTPlayerUtils.trimMemory() }
                    runCatching { AppProxy.connectionPool.evictAll() }
                    runCatching { SQLiteDatabase.releaseMemory() }
                }
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                imageCache?.trimToSize(imageCache.size / 2)
                LyricsUtils.releaseKuromoji()
                scope.launch(Dispatchers.IO) {
                    runCatching { SQLiteDatabase.releaseMemory() }
                }
            }
        }
        listeners.forEach { runCatching { it(level) } }
    }
}
