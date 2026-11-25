// NavigationBuilder.kt

package co.oto.music.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import co.oto.music.db.entities.FormatEntity
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable as navigationComposable
import androidx.navigation.compose.dialog
import co.oto.music.ui.utils.motionEnter
import co.oto.music.ui.utils.motionExit
import androidx.navigation.navArgument
import co.oto.music.R
import co.oto.music.LocalDatabase
import co.oto.music.constants.DarkModeKey
import co.oto.music.ui.theme.ForceDarkTheme
import co.oto.music.constants.PlaylistSortType
import co.oto.music.constants.PureBlackKey
import co.oto.music.db.entities.ArtistEntity
import co.oto.music.db.entities.SongArtistMap
import co.oto.music.db.entities.SongEntity
import co.oto.music.ui.component.ContainerCloseEasing
import co.oto.music.ui.component.ContainerCloseMillis
import co.oto.music.ui.component.ContainerOpenDamping
import co.oto.music.ui.component.ContainerOpenStiffness
import co.oto.music.ui.component.LocalNavAnimatedContentScope
import co.oto.music.ui.component.NavigationTitle
import co.oto.music.ui.screens.artist.ArtistAlbumsScreen
import co.oto.music.ui.screens.artist.ArtistItemsScreen
import co.oto.music.ui.screens.artist.ArtistScreen
import co.oto.music.ui.screens.artist.ArtistSongsScreen
import co.oto.music.ui.screens.playlist.AutoPlaylistScreen
import co.oto.music.ui.screens.playlist.CachePlaylistScreen
import co.oto.music.ui.screens.playlist.LocalPlaylistScreen
import co.oto.music.ui.screens.playlist.OnlinePlaylistScreen
import co.oto.music.ui.screens.playlist.TopPlaylistScreen
import co.oto.music.ui.screens.search.GenreScreen
import co.oto.music.ui.screens.search.OnlineSearchResult
import co.oto.music.ui.screens.search.SearchScreen
import co.oto.music.utils.rememberEnumPreference
import co.oto.music.utils.rememberPreference
import co.oto.music.ui.screens.settings.ProfileScreen
import co.oto.music.ui.screens.library.LibraryAlbumsScreen
import co.oto.music.ui.screens.SectionDetailScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import co.oto.music.viewmodels.HomeViewModel
import co.oto.music.ui.screens.library.LibraryArtistsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID

private val CoverRoutes = listOf("album/", "local_playlist/", "online_playlist/", "auto_playlist/", "module_store/")

private fun String?.opensFromCover(): Boolean = this != null && CoverRoutes.any { startsWith(it) }

// Home moves on the card's clocks: the open spring going out, the close ease coming back. the
// tiny threshold keeps the spring from snapping the last 1% of the scale at the end.
private val HomeRecedeSpring = spring(
    dampingRatio = ContainerOpenDamping,
    stiffness = ContainerOpenStiffness,
    visibilityThreshold = 0.001f,
)
private val HomeReturnEase = tween<Float>(ContainerCloseMillis, easing = ContainerCloseEasing)

/** Library screens recede like Home when a cover opens from them, and come back on the same clock. */
private fun libraryExitTo(route: String?): androidx.compose.animation.ExitTransition? =
    if (route.opensFromCover()) {
        scaleOut(HomeRecedeSpring, targetScale = 0.92f) + fadeOut(HomeRecedeSpring, targetAlpha = 0.45f)
    } else {
        null
    }

/** coming back from a cover page: the same clock as Home; anything else keeps the default */
private fun coverReturnFrom(route: String?): androidx.compose.animation.EnterTransition? =
    if (route.opensFromCover()) {
        scaleIn(HomeReturnEase, initialScale = 0.92f) + fadeIn(HomeReturnEase, initialAlpha = 0.45f)
    } else {
        null
    }

/**
 * a screen an album or playlist can be opened from (the Library's pages, Search, an artist,
 * Home's section pages): it hands its transition scope to its covers, so the page grows out of
 * the one tapped, and it sinks back while the page is open, exactly as Home does
 */
private fun NavGraphBuilder.coverSourceComposable(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable androidx.compose.animation.AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    exitTransition = { libraryExitTo(targetState.destination.route) },
    popEnterTransition = { coverReturnFrom(initialState.destination.route) },
) { entry ->
    CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
        content(entry)
    }
}

private fun libraryReturnFrom(route: String?): androidx.compose.animation.EnterTransition =
    if (route.opensFromCover()) {
        scaleIn(HomeReturnEase, initialScale = 0.92f) + fadeIn(HomeReturnEase, initialAlpha = 0.45f)
    } else {
        fadeIn(tween(150))
    }

private typealias NavEnter =
    androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition?
private typealias NavExit =
    androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition?

/**
 * Every page of this graph goes through here rather than straight to navigation's composable(), so
 * its own transitions answer Settings > Appearance > Animation (#10): off, the page swaps in place.
 * Same parameters and defaults as navigation's (a pop transition falls back to the push one, and a
 * transition that isn't given, or gives null, falls back to the NavHost's).
 */
private fun NavGraphBuilder.composable(
    route: String,
    arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    enterTransition: NavEnter? = null,
    exitTransition: NavExit? = null,
    popEnterTransition: NavEnter? = enterTransition,
    popExitTransition: NavExit? = exitTransition,
    content: @Composable androidx.compose.animation.AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit,
) = navigationComposable(
    route = route,
    arguments = arguments,
    enterTransition = { motionEnter { enterTransition?.invoke(this) } },
    exitTransition = { motionExit { exitTransition?.invoke(this) } },
    popEnterTransition = { motionEnter { popEnterTransition?.invoke(this) } },
    popExitTransition = { motionExit { popExitTransition?.invoke(this) } },
    // a module's page can stand in for any page here, every route at once (ModuleTakeover)
    content = { entry ->
        val scope = this
        co.oto.music.ui.screens.settings.modules.ModuleTakeover(route, entry) { scope.content(entry) }
    },
)

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    activity: Activity,
    snackbarHostState: SnackbarHostState
) {
    composable(
        route = Screens.Home.route,
        // opening a cover: Home sinks back and dims while the card grows toward you, then comes
        // forward again on the way back, in step with the card so a back swipe scrubs both
        exitTransition = {
            if (targetState.destination.route.opensFromCover()) {
                scaleOut(HomeRecedeSpring, targetScale = 0.92f) + fadeOut(HomeRecedeSpring, targetAlpha = 0.45f)
            } else {
                null
            }
        },
        popEnterTransition = {
            if (initialState.destination.route.opensFromCover()) {
                scaleIn(HomeReturnEase, initialScale = 0.92f) + fadeIn(HomeReturnEase, initialAlpha = 0.45f)
            } else {
                fadeIn(tween(150))
            }
        },
    ) {

        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            HomeScreen(navController = navController, snackbarHostState = snackbarHostState)
        }
    }

    coverSourceComposable(
        route = "section_detail/{sectionId}",
        arguments = listOf(
            navArgument("sectionId") {
                type = NavType.StringType
            },
        ),
    ) { backStackEntry ->
        val sectionId = backStackEntry.arguments?.getString("sectionId") ?: return@coverSourceComposable

        val homeViewModel: HomeViewModel = hiltViewModel(navController.getBackStackEntry(Screens.Home.route))
        SectionDetailScreen(
            navController = navController,
            sectionId = sectionId,
            homeViewModel = homeViewModel
        )
    }

    coverSourceComposable("history") {
        HistoryScreen(navController = navController)
    }

    composable(
        route = "coming_soon/{sectionTitle}",
        arguments = listOf(
            navArgument("sectionTitle") {
                type = NavType.StringType
            },
        ),
    ) { backStackEntry ->
        val sectionTitle = backStackEntry.arguments?.getString("sectionTitle") ?: return@composable
        ComingSoonScreen(navController = navController, sectionTitle = sectionTitle)
    }

    coverSourceComposable(Screens.Search.route) {
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val useDarkTheme = ForceDarkTheme
        val pureBlack = remember(pureBlackEnabled, useDarkTheme) {
            pureBlackEnabled && useDarkTheme
        }
        SearchScreen(
            navController = navController,
            pureBlack = pureBlack
        )
    }

    composable(
        route = "library",
        // a playlist opening from its cover: the Library sinks back while it grows, as Home does
        exitTransition = { libraryExitTo(targetState.destination.route) },
        popEnterTransition = { libraryReturnFrom(initialState.destination.route) },
    ) {
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val useDarkTheme = ForceDarkTheme
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            co.oto.music.ui.screens.library.LibraryHomeScreen(
                navController = navController,
                pureBlack = remember(pureBlackEnabled, useDarkTheme) { pureBlackEnabled && useDarkTheme },
            )
        }
    }

    coverSourceComposable("library/songs") {
        co.oto.music.ui.screens.library.LibrarySongsScreen(navController = navController)
    }

    composable(
        route = "library/playlists",
        exitTransition = { libraryExitTo(targetState.destination.route) },
        popEnterTransition = { libraryReturnFrom(initialState.destination.route) },
    ) {
        // the scope a playlist's cover needs to grow into its page, as on Home
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            co.oto.music.ui.screens.library.LibraryPlaylistsScreen(navController = navController)
        }
    }

    coverSourceComposable("library/added") {
        co.oto.music.ui.screens.library.LibraryRecentlyAddedScreen(navController = navController)
    }

    // one genre from the Library's Genres tab: the songs on the phone tagged with it
    coverSourceComposable(
        route = "local_genre/{name}",
        arguments = listOf(navArgument("name") { type = NavType.StringType }),
    ) {
        co.oto.music.ui.screens.library.LibraryGenreScreen(navController = navController)
    }

    // The Library's Local and Cached tabs open these: the same pages again, counting only that
    // tab's songs. "library/local/albums", "library/cached/albums" and so on.
    co.oto.music.viewmodels.LibraryShelfKind.entries.forEach { shelf ->
        coverSourceComposable("library/${shelf.key}/added") {
            co.oto.music.ui.screens.library.LibraryRecentlyAddedScreen(navController = navController, shelf = shelf)
        }

        coverSourceComposable("library/${shelf.key}/albums") {
            co.oto.music.ui.screens.library.LibraryAlbumsScreen(navController = navController, shelf = shelf)
        }

        coverSourceComposable("library/${shelf.key}/artists") {
            co.oto.music.ui.screens.library.LibraryArtistsScreen(navController = navController, shelf = shelf)
        }

        composable(
            route = "library/${shelf.key}/playlists",
            exitTransition = { libraryExitTo(targetState.destination.route) },
            popEnterTransition = { libraryReturnFrom(initialState.destination.route) },
        ) {
            CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
                co.oto.music.ui.screens.library.LibraryPlaylistsScreen(navController = navController, shelf = shelf)
            }
        }
    }

    // Everything one service brought in, whatever playlist it went into: Imported from Spotify.
    coverSourceComposable(
        route = "library/imported/{source}",
        arguments = listOf(navArgument("source") { type = NavType.StringType }),
    ) {
        co.oto.music.ui.screens.library.LibraryImportedScreen(navController = navController)
    }

    // The Imported tab's Playlists cards: the playlists one service brought in.
    composable(
        route = "library/imported/{source}/playlists",
        arguments = listOf(navArgument("source") { type = NavType.StringType }),
        exitTransition = { libraryExitTo(targetState.destination.route) },
        popEnterTransition = { libraryReturnFrom(initialState.destination.route) },
    ) { entry ->
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            co.oto.music.ui.screens.library.LibraryPlaylistsScreen(
                navController = navController,
                source = co.oto.music.importer.ImportSource.fromKey(entry.arguments?.getString("source")),
            )
        }
    }

    // the Imported tab's Albums cards: the albums one service's songs come from
    coverSourceComposable(
        route = "library/imported/{source}/albums",
        arguments = listOf(navArgument("source") { type = NavType.StringType }),
    ) {
        co.oto.music.ui.screens.library.LibraryAlbumsScreen(
            navController = navController,
            source = co.oto.music.importer.ImportSource.fromKey(it.arguments?.getString("source")),
        )
    }

    composable(Screens.Settings.route) {
        ProfileScreen(navController = navController)
    }

    composable("stats") {
        co.oto.music.ui.screens.recap.StatsScreen(navController = navController)
    }

    composable("cipher_settings") {
        co.oto.music.ui.screens.settings.CipherSettingsScreen(navController = navController)
    }

    composable("playback_diagnostics") {
        co.oto.music.ui.screens.settings.PlaybackDiagnosticsScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.SettingsListRoute) {
        // on a wide screen the list itself stays at the left (MainActivity), so this side waits for a pick
        if (co.oto.music.ui.utils.isWideLayout()) {
            co.oto.music.ui.screens.settings.SettingsPanePlaceholder()
        } else {
            co.oto.music.ui.screens.settings.OtoSettingsScreen(navController = navController)
        }
    }

    // app behaviour kept general's route, so old links and search results still land on it
    composable("general_settings") {
        co.oto.music.ui.screens.settings.AppBehaviourSettingsScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.PlaybackSettingsRoute) {
        co.oto.music.ui.screens.settings.PlaybackSettingsScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.NowPlayingSettingsRoute) {
        co.oto.music.ui.screens.settings.NowPlayingSettingsScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.integrations.IntegrationsRoute) {
        co.oto.music.ui.screens.settings.integrations.IntegrationsScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.integrations.spotify.SpotifyRoute) {
        co.oto.music.ui.screens.settings.integrations.spotify.SpotifyScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.integrations.discord.DiscordRoute) {
        co.oto.music.ui.screens.settings.integrations.discord.DiscordScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.integrations.backup.BackupRoute) {
        co.oto.music.ui.screens.settings.integrations.backup.BackupScreen(navController = navController)
    }

    composable(
        route = co.oto.music.ui.screens.settings.integrations.ScrobbleServiceRoute,
        arguments = listOf(navArgument("site") { type = NavType.StringType }),
    ) { entry ->
        val site = co.oto.music.scrobble.ScrobbleSite.entries
            .find { it.name == entry.arguments?.getString("site") }
            ?: co.oto.music.scrobble.ScrobbleSite.LASTFM
        co.oto.music.ui.screens.settings.integrations.ScrobbleServiceScreen(navController = navController, site = site)
    }

    composable(co.oto.music.ui.screens.settings.ProxySettingsRoute) {
        co.oto.music.ui.screens.settings.ProxySettingsScreen(navController = navController)
    }

    composable("crossmix_settings") {
        co.oto.music.ui.screens.settings.CrossmixSettingsScreen(navController = navController)
    }

    composable(
        route = "developer_space?person={person}",
        arguments = listOf(navArgument("person") { type = NavType.IntType; defaultValue = 0 }),
    ) { entry ->
        val person = entry.arguments?.getInt("person") ?: 0
        co.oto.music.ui.screens.settings.devspace.DeveloperSpaceScreen(
            navController = navController,
            initialPage = person,
        )
    }

    composable(co.oto.music.ui.screens.settings.devspace.DevPlaygroundRoute) {
        co.oto.music.ui.screens.settings.devspace.DevPlaygroundScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.storage.StorageRoute) {
        co.oto.music.ui.screens.settings.storage.StorageScreen(navController = navController)
    }

    composable("experimental_settings") {
        co.oto.music.ui.screens.settings.ExperimentalSettingsScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.importsync.ImportSyncRoute) {
        co.oto.music.ui.screens.settings.importsync.ImportSyncScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.importsync.ImportProvidersRoute) {
        co.oto.music.ui.screens.settings.importsync.ImportProvidersScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.importsync.ImportTuneMyMusicRoute) {
        co.oto.music.ui.screens.settings.importsync.TuneMyMusicImportScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.importsync.ImportProgressRoute) {
        co.oto.music.ui.screens.settings.importsync.ImportProgressScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.importsync.YouTubeSyncRoute) {
        co.oto.music.ui.screens.settings.importsync.YouTubeSyncScreen(navController = navController)
    }

    composable(
        route = co.oto.music.ui.screens.settings.importsync.AccountImportRoutePattern,
        arguments = listOf(
            navArgument("service") { type = NavType.StringType },
            // what a Re-sync opened the page for, and the services Re-sync all goes on to
            navArgument("resync") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("then") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) {
        co.oto.music.ui.screens.settings.importsync.AccountImportScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.importsync.YouTubeLoginRoute) {
        co.oto.music.ui.screens.settings.importsync.YouTubeLoginScreen(navController = navController)
    }

    composable("equalizer") {
        co.oto.music.ui.screens.equalizer.EqualizerScreen(navController = navController)
    }

    composable("gesture_settings") {
        co.oto.music.ui.screens.gesture.GestureScreen(navController = navController)
    }

    composable("customisation_settings") {
        co.oto.music.ui.screens.gesture.CustomisationScreen(navController = navController)
    }

    composable("audio_lab") {
        co.oto.music.ui.screens.audiolab.AudioLabScreen(navController = navController)
    }

    composable("audio_fx") {
        co.oto.music.ui.screens.audiolab.AudioFxScreen(navController = navController)
    }

    composable("audio_stage") {
        co.oto.music.ui.screens.audiolab.stage.StageScreen(navController = navController)
    }

    composable("audio_quality") {
        co.oto.music.ui.screens.audiolab.AudioQualityScreen(navController = navController)
    }

    composable("audio_pass") {
        co.oto.music.ui.screens.audiolab.AudioPassScreen(navController = navController)
    }

    composable(co.oto.music.ui.screens.settings.modules.ModulesRoute) {
        co.oto.music.ui.screens.settings.modules.ModulesScreen(navController = navController)
    }
    // a module's card grows into its store page like a playlist's cover, and the store recedes behind it
    coverSourceComposable(co.oto.music.ui.screens.settings.modules.ModuleBrowserRoute) {
        co.oto.music.ui.screens.settings.modules.ModuleBrowserScreen(navController = navController)
    }
    composable(
        route = co.oto.music.ui.screens.settings.modules.ModuleStoreRoutePattern,
        arguments = listOf(navArgument("post") { type = NavType.StringType }),
        enterTransition = { fadeIn(tween(300)) },
        popExitTransition = {
            scaleOut(animationSpec = tween(260), targetScale = 0.82f, transformOrigin = TransformOrigin(0.5f, 0.22f)) + fadeOut(tween(180))
        },
    ) { entry ->
        // the store page borrows the browser's list, so opening a module never waits on the feed again
        val browser = remember(entry) { navController.getBackStackEntry(co.oto.music.ui.screens.settings.modules.ModuleBrowserRoute) }
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            co.oto.music.ui.screens.settings.modules.ModuleStoreScreen(
                navController = navController,
                post = entry.arguments?.getString("post").orEmpty(),
                viewModel = hiltViewModel(browser),
            )
        }
    }

    composable(
        route = co.oto.music.ui.screens.settings.modules.ModuleRoutePattern,
        arguments = listOf(navArgument("id") { type = NavType.StringType }),
    ) { entry ->
        co.oto.music.ui.screens.settings.modules.ModuleScreen(
            navController = navController,
            id = entry.arguments?.getString("id").orEmpty(),
        )
    }

    composable(
        route = co.oto.music.ui.screens.settings.modules.ModulePageRoutePattern,
        arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("page") { type = NavType.StringType }),
    ) { entry ->
        co.oto.music.ui.screens.settings.modules.ModulePageScreen(
            navController = navController,
            id = entry.arguments?.getString("id").orEmpty(),
            pageId = entry.arguments?.getString("page").orEmpty(),
        )
    }

    composable(
        route = "browse/{browseId}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
            }
        )
    ) {
        BrowseScreen(
            navController,
            scrollBehavior,
            it.arguments?.getString("browseId")
        )
    }

    composable(
        route = "search/{query}",
        arguments = listOf(
            navArgument("query") {
                type = NavType.StringType
            },
        ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            when {
                targetState.destination.route.opensFromCover() -> libraryExitTo(targetState.destination.route)
                targetState.destination.route?.startsWith("search/") == true -> fadeOut(tween(200))
                else -> fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            when {
                initialState.destination.route.opensFromCover() -> coverReturnFrom(initialState.destination.route)
                initialState.destination.route?.startsWith("search/") == true -> fadeIn(tween(250))
                else -> fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            OnlineSearchResult(navController)
        }
    }

    composable(
        route = "album/{albumId}",
        arguments = listOf(
            navArgument("albumId") {
                type = NavType.StringType
            },
        ),

        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { libraryExitTo(targetState.destination.route) ?: fadeOut(tween(200)) },
        popEnterTransition = { coverReturnFrom(initialState.destination.route) ?: fadeIn(tween(300)) },

        popExitTransition = {
            scaleOut(
                animationSpec = tween(260),
                targetScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0.22f),
            ) + fadeOut(tween(180))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            AlbumScreen(navController, scrollBehavior)
        }
    }

    coverSourceComposable(
        route = "artist/{artistId}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        ArtistScreen(navController, scrollBehavior)
    }

    coverSourceComposable(
        route = "artist/{artistId}/songs",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }

    coverSourceComposable(
        route = "artist/{artistId}/albums",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            }
        )
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }

    // "similar to <artist>": the artists, playlists and songs around one artist, from its page
    coverSourceComposable(
        route = "artist/{artistId}/similar",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        co.oto.music.ui.screens.artist.ArtistSimilarScreen(navController)
    }

    coverSourceComposable(
        route = "artist/{artistId}/items?browseId={browseId}?params={params}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }

    composable(
        route = "online_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { libraryExitTo(targetState.destination.route) ?: fadeOut(tween(200)) },
        popEnterTransition = { coverReturnFrom(initialState.destination.route) ?: fadeIn(tween(300)) },

        popExitTransition = {
            scaleOut(
                animationSpec = tween(260),
                targetScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0.22f),
            ) + fadeOut(tween(180))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            OnlinePlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "local_playlist/{playlistId}?mixIt={mixIt}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
            navArgument("mixIt") {
                type = NavType.BoolType
                defaultValue = false
            },
        ),
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { libraryExitTo(targetState.destination.route) ?: fadeOut(tween(200)) },
        popEnterTransition = { coverReturnFrom(initialState.destination.route) ?: fadeIn(tween(300)) },

        popExitTransition = {
            scaleOut(
                animationSpec = tween(260),
                targetScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0.22f),
            ) + fadeOut(tween(180))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            LocalPlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "auto_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { libraryExitTo(targetState.destination.route) ?: fadeOut(tween(200)) },
        popEnterTransition = { coverReturnFrom(initialState.destination.route) ?: fadeIn(tween(300)) },

        popExitTransition = {
            scaleOut(
                animationSpec = tween(260),
                targetScale = 0.82f,
                transformOrigin = TransformOrigin(0.5f, 0.22f),
            ) + fadeOut(tween(180))
        },
    ) {
        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
            AutoPlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "cache_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }

    composable(
        route = "top_playlist/{top}",
        arguments = listOf(
            navArgument("top") {
                type = NavType.StringType
            },
        ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }

    coverSourceComposable(
        route = "genre/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val useDarkTheme = ForceDarkTheme
        GenreScreen(
            navController = navController,
            pureBlack = remember(pureBlackEnabled, useDarkTheme) {
                pureBlackEnabled && useDarkTheme
            },
        )
    }

    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        YouTubeBrowseScreen(navController)
    }

    coverSourceComposable("library/albums") {
        co.oto.music.ui.screens.library.LibraryAlbumsScreen(navController = navController)
    }

    coverSourceComposable("library/artists") {
        co.oto.music.ui.screens.library.LibraryArtistsScreen(navController = navController)
    }
}
