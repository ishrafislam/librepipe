package app.librepipes.ui

import android.content.Context
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Rational
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.librepipes.LibrePipeApp
import app.librepipes.data.model.StreamRef
import app.librepipes.player.EXTRA_WATCH_URL
import app.librepipes.player.PlaybackOpener
import app.librepipes.player.WatchRequest
import app.librepipes.player.PopupLauncher
import app.librepipes.ui.components.kit.LpBottomBar
import app.librepipes.ui.components.kit.LpMiniPlayer
import app.librepipes.ui.components.kit.LpNavItem
import app.librepipes.ui.components.kit.LpSplashScreen
import app.librepipes.ui.screens.ChannelScreen
import app.librepipes.ui.screens.DownloadsScreen
import app.librepipes.ui.screens.HistoryScreen
import app.librepipes.ui.screens.HomeScreen
import app.librepipes.ui.screens.LibraryScreen
import app.librepipes.ui.screens.LocalPlaylistScreen
import app.librepipes.ui.screens.PlaylistScreen
import app.librepipes.ui.screens.SearchScreen
import app.librepipes.ui.screens.SettingsScreen
import app.librepipes.ui.screens.SubscriptionsScreen
import app.librepipes.ui.screens.WatchScreen
import app.librepipes.ui.theme.LibrePipeTheme
import app.librepipes.ui.theme.Motion
import app.librepipes.ui.viewmodels.ChannelViewModel
import app.librepipes.ui.viewmodels.DownloadsViewModel
import app.librepipes.ui.viewmodels.HistoryViewModel
import app.librepipes.ui.viewmodels.HomeViewModel
import app.librepipes.ui.viewmodels.LibraryViewModel
import app.librepipes.ui.viewmodels.LocalPlaylistViewModel
import app.librepipes.ui.viewmodels.MiniPlayerViewModel
import app.librepipes.ui.viewmodels.PlaylistViewModel
import app.librepipes.ui.viewmodels.SearchViewModel
import app.librepipes.ui.viewmodels.SettingsViewModel
import app.librepipes.ui.viewmodels.SubscriptionsViewModel
import app.librepipes.ui.viewmodels.WatchViewModel
import app.librepipes.ui.viewmodels.appViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SUBSCRIPTIONS = "subscriptions"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads"
    const val CHANNEL = "channel/{url}"
    const val PLAYLIST = "playlist/{url}"
    const val LOCAL_PLAYLIST = "localplaylist/{id}"
    const val WATCH = "watch/{url}"

    fun watch(url: String) = "watch/${Uri.encode(url)}"
    fun channel(url: String) = "channel/${Uri.encode(url)}"
    fun playlist(url: String) = "playlist/${Uri.encode(url)}"
    fun localPlaylist(id: Long) = "localplaylist/$id"
}

class MainActivity : ComponentActivity() {

    private var deepLink by mutableStateOf<String?>(null)
    private var watchUrl by mutableStateOf<String?>(null)

    /** True while the system has us in a picture-in-picture window. */
    private var pipMode by mutableStateOf(false)

    /** Set by the watch route so onUserLeaveHint knows PiP is meaningful right now. */
    private var watchActive = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink = extractYoutubeUrl(intent)
        watchUrl = intent.getStringExtra(EXTRA_WATCH_URL)

        setContent {
            val app = application as LibrePipeApp
            val themePref by app.container.settings.theme.collectAsState(initial = 0)
            val dynamicColorPref by app.container.settings.dynamicColor.collectAsState(initial = true)
            val darkTheme = when (themePref) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            LibrePipeTheme(darkTheme = darkTheme, dynamicColor = dynamicColorPref) {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(400)
                    showSplash = false
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        deepLink = deepLink,
                        watchUrl = watchUrl,
                        pipMode = pipMode,
                        onRequestNotificationPermission = { requestNotificationPermission() },
                        onDeepLinkConsumed = { deepLink = null },
                        onWatchUrlConsumed = { watchUrl = null },
                        onWatchActiveChanged = { watchActive = it },
                        onEnterPip = { enterPip() },
                        onFullscreenChanged = { setFullscreen(it) },
                    )
                    AnimatedVisibility(
                        visible = showSplash,
                        exit = fadeOut(animationSpec = tween(500, easing = Motion.Emphasized)),
                    ) {
                        LpSplashScreen(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractYoutubeUrl(intent)?.let { deepLink = it }
        intent.getStringExtra(EXTRA_WATCH_URL)?.let { watchUrl = it }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (watchActive) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode = isInPictureInPictureMode
    }

    private fun enterPip() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
            )
        }
    }

    /** Landscape + hidden system bars, restored on exit. */
    private fun setFullscreen(on: Boolean) {
        requestedOrientation = if (on) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onResume() {
        super.onResume()
        PopupLauncher.consumePending(this)
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun extractYoutubeUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        val host = data.host ?: return null
        return if (host.contains("youtube.com") || host.contains("youtu.be")) {
            data.toString()
        } else {
            null
        }
    }
}

@Composable
private fun MainScreen(
    deepLink: String?,
    watchUrl: String?,
    pipMode: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onDeepLinkConsumed: () -> Unit,
    onWatchUrlConsumed: () -> Unit,
    onWatchActiveChanged: (Boolean) -> Unit,
    onEnterPip: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val app = context.applicationContext as LibrePipeApp
    val unreadCount by app.container.subscriptions.observeUnreadCount().collectAsState(initial = 0)

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            handleDeepLink(deepLink, navController)
            onDeepLinkConsumed()
        }
    }
    LaunchedEffect(watchUrl) {
        if (watchUrl != null) {
            navController.navigate(Routes.watch(watchUrl))
            onWatchUrlConsumed()
        }
    }

    val openVideo: (StreamRef, List<StreamRef>) -> Unit = { ref, queue ->
        WatchRequest.set(ref, queue)
        navController.navigate(Routes.watch(ref.url))
    }
    val openChannel: (String) -> Unit = { url -> navController.navigate(Routes.channel(url)) }
    val openPlaylist: (String) -> Unit = { url -> navController.navigate(Routes.playlist(url)) }
    val playUri: (Uri, String) -> Unit = { uri, title ->
        scope.launch { PlaybackOpener.playUri(context, uri, title) }
    }
    val openSearch: () -> Unit = { navController.navigate(Routes.SEARCH) }

    // No queue here on purpose: the session already holds one, and startSession keeps
    // an existing timeline when the id matches.
    val openMiniPlayer: (StreamRef) -> Unit = { ref ->
        WatchRequest.set(ref, emptyList())
        navController.navigate(Routes.watch(ref.url))
    }

    val onNavigate: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val onWatchRoute = currentRoute == Routes.WATCH
    var fullscreen by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    // Rotating to landscape while watching enters fullscreen on its own; leaving the
    // route or returning to portrait exits it.
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(onWatchRoute, landscape) {
        if (!onWatchRoute) {
            fullscreen = false
            locked = false
        } else if (landscape) {
            fullscreen = true
        }
    }
    LaunchedEffect(fullscreen) { onFullscreenChanged(fullscreen) }
    LaunchedEffect(onWatchRoute) { onWatchActiveChanged(onWatchRoute) }

    val tabRoutes = listOf(Routes.HOME, Routes.SUBSCRIPTIONS, Routes.LIBRARY, Routes.SETTINGS)
    // Search is pushed from Home, so Home stays the active tab while searching.
    val selectedIndex = when (currentRoute) {
        Routes.SEARCH -> 0
        else -> tabRoutes.indexOf(currentRoute)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        if (fullscreen || pipMode) {
            // No app chrome at all: landscape crosses the 840dp threshold, so without
            // this the wide layout would put the nav rail beside a "fullscreen" video.
            AppNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize(),
                openVideo = openVideo,
                openChannel = openChannel,
                openPlaylist = openPlaylist,
                openSearch = openSearch,
                playUri = playUri,
                onRequestNotificationPermission = onRequestNotificationPermission,
                fullscreen = fullscreen,
                pipMode = pipMode,
                onEnterPip = onEnterPip,
                onToggleFullscreen = { fullscreen = !fullscreen },
                locked = locked,
                onSetLocked = { locked = it },
            )
        } else if (wide) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (!onWatchRoute) {
                    NavRail(currentRoute = currentRoute, onNavigate = onNavigate, unreadCount = unreadCount)
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier.weight(1f),
                        openVideo = openVideo,
                        openChannel = openChannel,
                        openPlaylist = openPlaylist,
                        openSearch = openSearch,
                        playUri = playUri,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        fullscreen = fullscreen,
                        pipMode = pipMode,
                        onEnterPip = onEnterPip,
                        onToggleFullscreen = { fullscreen = !fullscreen },
                        locked = locked,
                        onSetLocked = { locked = it },
                    )
                    if (!onWatchRoute) MiniPlayerHost(onOpen = openMiniPlayer)
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    // The watch page is the player; a mini player under it would be
                    // duplicate chrome, and fullscreen/PiP want the whole window.
                    if (!onWatchRoute && !pipMode) {
                        Column {
                            MiniPlayerHost(onOpen = openMiniPlayer)
                            LpBottomBar(
                                items = bottomNavItems(unreadCount),
                                selectedIndex = selectedIndex,
                                onSelect = { index -> onNavigate(tabRoutes[index]) },
                            )
                        }
                    }
                },
            ) { padding ->
                AppNavHost(
                    navController = navController,
                    modifier = Modifier.padding(padding),
                    openVideo = openVideo,
                    openChannel = openChannel,
                    openPlaylist = openPlaylist,
                    openSearch = openSearch,
                    playUri = playUri,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    fullscreen = fullscreen,
                    pipMode = pipMode,
                    onEnterPip = onEnterPip,
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    locked = locked,
                    onSetLocked = { locked = it },
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerHost(onOpen: (StreamRef) -> Unit) {
    val vm: MiniPlayerViewModel = appViewModel { MiniPlayerViewModel(it) }
    val state by vm.uiState.collectAsState()
    val ref = state.ref
    if (!state.visible || ref == null) return
    LpMiniPlayer(
        title = state.title,
        channelName = state.channelName,
        thumbnailUrl = state.thumbnailUrl,
        progress = state.progress,
        onClick = { onOpen(ref) },
        isPlaying = state.isPlaying,
        isLive = state.isLive,
        onPlayPause = vm::playPause,
        onClose = vm::stop,
    )
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    openVideo: (StreamRef, List<StreamRef>) -> Unit,
    openChannel: (String) -> Unit,
    openPlaylist: (String) -> Unit,
    openSearch: () -> Unit,
    playUri: (Uri, String) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    fullscreen: Boolean,
    pipMode: Boolean,
    onEnterPip: () -> Unit,
    onToggleFullscreen: () -> Unit,
    locked: Boolean,
    onSetLocked: (Boolean) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(appViewModel { HomeViewModel(it) }, openVideo, openSearch)
        }
        composable(
            Routes.WATCH,
            arguments = listOf(navArgument("url") { type = NavType.StringType }),
        ) { entry ->
            val url = Uri.decode(entry.arguments?.getString("url").orEmpty())
            val request = remember(url) { WatchRequest.take(url) }
            val ref = request?.first ?: StreamRef(
                id = url.substringAfterLast("v="),
                title = "",
                url = url,
            )
            WatchScreen(
                vm = appViewModel { WatchViewModel(it, ref, request?.second.orEmpty()) },
                fullscreen = fullscreen,
                pipMode = pipMode,
                locked = locked,
                onMinimize = { navController.popBackStack() },
                onEnterPip = onEnterPip,
                onToggleFullscreen = onToggleFullscreen,
                onSetLocked = onSetLocked,
                onOpenChannel = openChannel,
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                vm = appViewModel { SearchViewModel(it) },
                onBack = { navController.popBackStack() },
                onOpenVideo = openVideo,
                onOpenChannel = openChannel,
                onOpenPlaylist = openPlaylist,
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                vm = appViewModel { LibraryViewModel(it) },
                onOpenLocalPlaylist = { id -> navController.navigate(Routes.localPlaylist(id)) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                vm = appViewModel { HistoryViewModel(it) },
                onBack = { navController.popBackStack() },
                onOpenVideo = openVideo,
            )
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                vm = appViewModel { DownloadsViewModel(it) },
                onBack = { navController.popBackStack() },
                onPlayUri = playUri,
                onOpenVideo = openVideo,
            )
        }
        composable(Routes.SUBSCRIPTIONS) {
            SubscriptionsScreen(
                appViewModel { SubscriptionsViewModel(it) },
                openVideo,
                openChannel,
                openSearch,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                vm = appViewModel { SettingsViewModel(it) },
                onRequestNotificationPermission = onRequestNotificationPermission,
            )
        }
        composable(
            route = Routes.CHANNEL,
            arguments = listOf(navArgument("url") { type = NavType.StringType }),
        ) { entry ->
            val url = entry.arguments?.getString("url") ?: return@composable
            ChannelScreen(
                vm = appViewModel { ChannelViewModel(it, url) },
                onBack = { navController.popBackStack() },
                onOpenVideo = openVideo,
            )
        }
        composable(
            route = Routes.PLAYLIST,
            arguments = listOf(navArgument("url") { type = NavType.StringType }),
        ) { entry ->
            val url = entry.arguments?.getString("url") ?: return@composable
            PlaylistScreen(
                vm = appViewModel { PlaylistViewModel(it, url) },
                onBack = { navController.popBackStack() },
                onOpenVideo = openVideo,
            )
        }
        composable(
            route = Routes.LOCAL_PLAYLIST,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            LocalPlaylistScreen(
                vm = appViewModel { LocalPlaylistViewModel(it, id) },
                onBack = { navController.popBackStack() },
                onOpenVideo = openVideo,
            )
        }
    }
}

@Composable
private fun NavRail(currentRoute: String?, onNavigate: (String) -> Unit, unreadCount: Int) {
    val items = bottomNavItems(unreadCount)
    val routes = listOf(Routes.HOME, Routes.SUBSCRIPTIONS, Routes.LIBRARY, Routes.SETTINGS)
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        items.forEachIndexed { index, item ->
            val selected = currentRoute == routes[index]
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(routes[index]) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.iconFilled else item.icon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label) },
            )
        }
    }
}

private fun bottomNavItems(unreadCount: Int) = listOf(
    LpNavItem("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    LpNavItem("Subs", Icons.Outlined.Subscriptions, Icons.Rounded.Subscriptions, unreadCount = unreadCount),
    LpNavItem("Library", Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary),
    LpNavItem("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

private fun handleDeepLink(
    url: String,
    navController: NavHostController,
) {
    val uri = Uri.parse(url)
    val host = uri.host.orEmpty()
    val videoId = when {
        host.contains("youtu.be") -> uri.lastPathSegment
        host.contains("youtube.com") -> when {
            uri.path == "/watch" -> uri.getQueryParameter("v")
            uri.path?.startsWith("/shorts/") == true -> uri.pathSegments.getOrNull(1)
            uri.path?.startsWith("/live/") == true -> uri.pathSegments.getOrNull(1)
            else -> null
        }
        else -> null
    }
    if (videoId != null) {
        navController.navigate(Routes.watch("https://www.youtube.com/watch?v=$videoId"))
        return
    }

    when {
        uri.path?.startsWith("/channel/") == true ||
            uri.path?.startsWith("/@") == true ||
            uri.path?.startsWith("/c/") == true -> navController.navigate(Routes.channel(url))

        uri.getQueryParameter("list") != null -> navController.navigate(
            Routes.playlist("https://www.youtube.com/playlist?list=${uri.getQueryParameter("list")}")
        )
    }
}
