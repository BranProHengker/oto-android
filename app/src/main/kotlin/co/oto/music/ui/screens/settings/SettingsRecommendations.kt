// the recommended card at the top of settings: tips for things you haven't set up yet, the ones that fit right now first

package co.oto.music.ui.screens.settings

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.Preferences
import co.oto.music.R
import co.oto.music.constants.AudioQualityKey
import co.oto.music.constants.BlurQualityKey
import co.oto.music.constants.CanvasThumbnailAnimationKey
import co.oto.music.constants.CrossmixMode
import co.oto.music.constants.DataSaverKey
import co.oto.music.constants.DiscordAccessTokenKey
import co.oto.music.constants.FloatingLyricsKey
import co.oto.music.constants.HomeStyleKey
import co.oto.music.constants.LarpModeKey
import co.oto.music.constants.LastFMSessionKey
import co.oto.music.constants.LibreFmSessionKey
import co.oto.music.constants.LyricsMotionStyleKey
import co.oto.music.constants.PauseOnMute
import co.oto.music.constants.PlayerStyleKey
import co.oto.music.constants.ResumeOnBluetoothConnectKey
import co.oto.music.constants.SwipeLeftActionKey
import co.oto.music.constants.SwipeRightActionKey
import co.oto.music.constants.transitionModeOf
import co.oto.music.di.AudioLabEntryPoint
import co.oto.music.eq.EqSettings
import co.oto.music.playback.audio.AudioOutputs
import co.oto.music.scrobble.ScrobbleSite
import co.oto.music.ui.component.SettingsHighlight
import co.oto.music.ui.screens.devices.DeviceIntegrationRoute
import co.oto.music.ui.screens.settings.importsync.ImportSyncRoute
import co.oto.music.ui.screens.settings.integrations.discord.DiscordRoute
import co.oto.music.ui.screens.settings.integrations.scrobbleServiceRoute
import co.oto.music.ui.theme.InterFontFamily
import co.oto.music.utils.PreferencesCache
import co.oto.music.utils.dataStore
import dagger.hilt.android.EntryPointAccessors

// what the tips look at before they show; settings and audio lab are already in memory, the network and outputs are one quick ask each
internal class RecommendationState(
    val prefs: Preferences,
    val eq: EqSettings,
    val spatialOn: Boolean,
    val metered: Boolean,
    val headphones: Boolean,
    val usbDac: Boolean,
) {
    companion object {
        fun read(context: Context): RecommendationState {
            val app = context.applicationContext
            val audioLab = EntryPointAccessors.fromApplication(app, AudioLabEntryPoint::class.java).audioLabStore()
            val outputs = runCatching { app.getSystemService<AudioManager>()?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList() }
                .getOrNull().orEmpty()
                .mapNotNull { AudioOutputs.kindOf(it.type) }
            return RecommendationState(
                prefs = PreferencesCache.current(app.dataStore),
                eq = audioLab.eq.value,
                spatialOn = audioLab.spatial.value.enabled,
                metered = app.getSystemService<ConnectivityManager>()?.isActiveNetworkMetered == true,
                headphones = outputs.any { it == AudioOutputs.Kind.WIRED || it == AudioOutputs.Kind.BLUETOOTH || it == AudioOutputs.Kind.USB },
                usbDac = AudioOutputs.Kind.USB in outputs,
            )
        }
    }
}

internal class RecommendedSetting(
    @StringRes val titleRes: Int,
    // the settings page it lives under, shown as "Settings › page"
    @StringRes val pageRes: Int,
    val route: String,
    val icon: @Composable () -> Painter,
    // the row or card that flashes when the page opens
    val highlight: String? = null,
    // false once there's nothing left to suggest, like a switch that's already on
    val applies: (RecommendationState) -> Boolean = { true },
    // fits what's going on right now (headphones in, on mobile data), so it goes ahead of the rest
    val timely: (RecommendationState) -> Boolean = { false },
)

// a style tip only shows to people who never touched that style
private fun untouched(vararg keys: Preferences.Key<*>): (RecommendationState) -> Boolean =
    { state -> keys.all { state.prefs[it] == null } }

private val RecommendedOptions = listOf(
    // audio lab
    RecommendedSetting(
        titleRes = R.string.settings_rec_autoeq,
        pageRes = R.string.settings_audio_lab_category,
        route = "equalizer",
        icon = { painterResource(R.drawable.headset_applemusic) },
        applies = { it.eq.correction == null },
        timely = { it.headphones },
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_equalizer,
        pageRes = R.string.settings_audio_lab_category,
        route = "equalizer",
        icon = { painterResource(R.drawable.equalizer) },
        applies = { !it.eq.enabled },
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_spatial,
        pageRes = R.string.settings_audio_lab_category,
        route = "audio_fx",
        icon = { painterResource(R.drawable.spatial_tracking_apple) },
        applies = { !it.spatialOn },
        timely = { it.headphones },
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_audio_pass,
        pageRes = R.string.settings_audio_lab_category,
        route = "audio_pass",
        icon = { rememberVectorPainter(Icons.Rounded.Usb) },
        applies = { it.usbDac },
        timely = { it.usbDac },
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_crossfade,
        pageRes = R.string.settings_audio_lab_category,
        route = "crossmix_settings",
        icon = { painterResource(R.drawable.linear_scale) },
        applies = { transitionModeOf(it.prefs) == CrossmixMode.OFF },
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_audio_quality,
        pageRes = R.string.settings_audio_lab_category,
        route = "audio_quality",
        icon = { rememberVectorPainter(Icons.Rounded.HighQuality) },
        applies = untouched(AudioQualityKey),
    ),

    // playback and now playing
    RecommendedSetting(
        titleRes = R.string.settings_rec_bluetooth,
        pageRes = R.string.settings_playback_category,
        route = PlaybackSettingsRoute,
        icon = { painterResource(R.drawable.bluetooth) },
        highlight = "Resume on Bluetooth connection",
        applies = { it.prefs[ResumeOnBluetoothConnectKey] != true },
        timely = { it.headphones },
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_pause_muted,
        pageRes = R.string.settings_playback_category,
        route = PlaybackSettingsRoute,
        icon = { painterResource(R.drawable.volume_off_pause) },
        highlight = "Pause when muted",
        applies = { it.prefs[PauseOnMute] != true },
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_floating_lyrics,
        pageRes = R.string.settings_now_playing,
        route = NowPlayingSettingsRoute,
        icon = { rememberVectorPainter(Icons.Rounded.PictureInPictureAlt) },
        highlight = "Floating lyrics overlay bubble",
        applies = { it.prefs[FloatingLyricsKey] != true },
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_canvas,
        pageRes = R.string.settings_now_playing,
        route = NowPlayingSettingsRoute,
        icon = { painterResource(R.drawable.sparks) },
        highlight = "Enable animated canvas",
        applies = { it.prefs[CanvasThumbnailAnimationKey] == false },
    ),

    // the app's own behaviour
    RecommendedSetting(
        titleRes = R.string.settings_rec_data_saver,
        pageRes = R.string.settings_app_behaviour,
        route = "general_settings",
        icon = { rememberVectorPainter(Icons.Rounded.DataSaverOn) },
        highlight = "Data saver",
        applies = { it.metered && it.prefs[DataSaverKey] != true },
        timely = { it.metered },
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_larp,
        pageRes = R.string.settings_app_behaviour,
        route = "general_settings",
        icon = { rememberVectorPainter(Icons.Rounded.AutoAwesome) },
        highlight = "Larp mode",
        applies = { it.prefs[LarpModeKey] != true },
    ),

    // customisation
    RecommendedSetting(
        titleRes = R.string.settings_rec_blur,
        pageRes = R.string.settings_customisation_category,
        route = "customisation_settings",
        icon = { painterResource(R.drawable.contrast) },
        highlight = "Blur",
        applies = untouched(BlurQualityKey),
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_lyrics_animation,
        pageRes = R.string.settings_customisation_category,
        route = "customisation_settings",
        icon = { painterResource(R.drawable.lyrics) },
        highlight = "Lyrics animation",
        applies = untouched(LyricsMotionStyleKey),
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_player_style,
        pageRes = R.string.settings_customisation_category,
        route = "customisation_settings",
        icon = { painterResource(R.drawable.palette) },
        highlight = "Player styles",
        applies = untouched(PlayerStyleKey),
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_home_style,
        pageRes = R.string.settings_customisation_category,
        route = "customisation_settings",
        icon = { painterResource(R.drawable.home) },
        highlight = "Home styles",
        applies = untouched(HomeStyleKey),
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_gestures,
        pageRes = R.string.settings_customisation_category,
        route = "customisation_settings",
        icon = { painterResource(R.drawable.swipe) },
        highlight = "Gesture",
        applies = untouched(SwipeRightActionKey, SwipeLeftActionKey),
    ),
    // launcher icons only switch on android 8+, the customize page hides the card below that
    RecommendedSetting(
        titleRes = R.string.settings_rec_app_icon,
        pageRes = R.string.settings_customisation_category,
        route = "customisation_settings",
        icon = { painterResource(R.drawable.grid_view) },
        highlight = "Icons",
        applies = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.O },
    ),

    // other apps and devices
    RecommendedSetting(
        titleRes = R.string.settings_rec_lastfm,
        pageRes = R.string.integrations,
        route = scrobbleServiceRoute(ScrobbleSite.LASTFM),
        icon = { painterResource(R.drawable.music_history) },
        applies = { it.prefs[LastFMSessionKey].isNullOrEmpty() && it.prefs[LibreFmSessionKey].isNullOrEmpty() },
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_discord,
        pageRes = R.string.integrations,
        route = DiscordRoute,
        icon = { painterResource(R.drawable.discord) },
        applies = { it.prefs[DiscordAccessTokenKey].isNullOrEmpty() },
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_import,
        pageRes = R.string.experimental_import_sync_title,
        route = ImportSyncRoute,
        icon = { painterResource(R.drawable.sync) },
    ),
    RecommendedSetting(
        titleRes = R.string.settings_rec_devices,
        pageRes = R.string.devices_title,
        route = DeviceIntegrationRoute,
        icon = { painterResource(R.drawable.cast_connected) },
    ),
)

// the tips that still apply, timely ones first, never two for the same page so the card stays varied
internal fun pickRecommendations(context: Context, count: Int = 2): List<RecommendedSetting> {
    val state = RecommendationState.read(context)
    val (timely, rest) = RecommendedOptions.filter { it.applies(state) }.partition { it.timely(state) }
    return (timely.shuffled() + rest.shuffled()).distinctBy { it.route }.take(count)
}

@Composable
internal fun RecommendedCard(recommendations: List<RecommendedSetting>, open: (String) -> Unit) {
    if (recommendations.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(BubbleColor)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_recommended_item),
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp),
        )
        recommendations.forEachIndexed { index, recommendation ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 66.dp, end = 10.dp)
                        .height(0.8.dp)
                        .background(Color.White.copy(alpha = 0.08f)),
                )
            }
            RecommendationRow(
                recommendation = recommendation,
                onClick = {
                    recommendation.highlight?.let { SettingsHighlight.pending = it }
                    open(recommendation.route)
                },
            )
        }
    }
}

@Composable
private fun RecommendationRow(recommendation: RecommendedSetting, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(IconBgColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = recommendation.icon(),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(recommendation.titleRes),
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.settings_crumb, stringResource(recommendation.pageRes)),
                fontFamily = InterFontFamily,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f),
            )
        }
    }
}
