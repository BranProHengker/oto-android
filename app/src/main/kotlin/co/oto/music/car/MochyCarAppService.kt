// OtoCarAppService.kt

package co.oto.music.car

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import androidx.media3.common.util.UnstableApi
import co.oto.music.db.MusicDatabase
import co.oto.music.link.phone.PhoneLinkHost
import co.oto.music.link.phone.PhonePairing
import co.oto.music.lyrics.LyricsHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// oto's own android auto screens, drawn by the car from google's media templates so they look and size like every other car app
@UnstableApi
@AndroidEntryPoint
class OtoCarAppService : CarAppService() {
    @Inject lateinit var database: MusicDatabase
    @Inject lateinit var lyricsHelper: LyricsHelper
    @Inject lateinit var pairing: PhonePairing
    @Inject lateinit var link: PhoneLinkHost

    // a debug build talks to any host, like the desktop head unit; anything else only to android auto and the car makers google lists
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session =
        CarSession(database, lyricsHelper, pairing, link)
}
