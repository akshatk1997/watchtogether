package com.example

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.GuestSession
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.MediaLookupService
import com.example.data.SyncEngine
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.WatchTogetherRepository
import com.example.ui.components.CinematicBackdrop
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LiveTvScreen
import com.example.ui.screens.RoomScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.WatchTogetherTheme
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.RoomViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_HOME = "home"
private const val ROUTE_LIVE_TV = "live_tv"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ROOM = "room/{roomCode}"

private data class TopDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {

    private val isInPipState = mutableStateOf(false)
    private val pendingDeepLinkCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractDeepLinkRoomCode(intent)

        setContent {
            val isInPip by isInPipState
            val deepLinkCode by pendingDeepLinkCode

            WatchTogetherRoot(
                isInPipMode = isInPip,
                pendingRoomCode = deepLinkCode,
                onConsumePendingRoomCode = { pendingDeepLinkCode.value = null },
                onRequestPip = { enterWatchTogetherPip() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractDeepLinkRoomCode(intent)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipState.value = isInPictureInPictureMode
    }

    private fun extractDeepLinkRoomCode(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "watchtogether" && data.host == "room") {
            val raw = data.lastPathSegment ?: return
            val normalized = SyncEngine.normalizeRoomCode(raw)
            if (SyncEngine.isValidRoomCode(normalized)) {
                pendingDeepLinkCode.value = normalized
            }
        }
    }

    private fun enterWatchTogetherPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            runCatching { enterPictureInPictureMode(params) }
        }
    }
}

@Composable
private fun WatchTogetherRoot(
    isInPipMode: Boolean,
    pendingRoomCode: String?,
    onConsumePendingRoomCode: () -> Unit,
    onRequestPip: () -> Unit
) {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val guestUid by GuestSession.guestUid.collectAsStateWithLifecycle()

    val user = currentUser
    val activeUid = user?.uid ?: guestUid
    if (activeUid == null) {
        WatchTogetherTheme(darkTheme = true) {
            AuthScreen(onAuthSuccess = {})
        }
    } else {
        key(activeUid) {
            AuthenticatedWatchTogetherApp(
                currentUserId = activeUid,
                userDisplayName = user?.displayName ?: if (guestUid != null) "Guest" else "Cinema Fan",
                userEmail = user?.email,
                isInPipMode = isInPipMode,
                pendingRoomCode = pendingRoomCode,
                onConsumePendingRoomCode = onConsumePendingRoomCode,
                onRequestPip = onRequestPip
            )
        }
    }
}

@Composable
private fun AuthenticatedWatchTogetherApp(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String?,
    isInPipMode: Boolean,
    pendingRoomCode: String?,
    onConsumePendingRoomCode: () -> Unit,
    onRequestPip: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val appViewModel: AppViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AppViewModel(
                    repository = WatchTogetherRepository(db),
                    mediaLookupService = MediaLookupService(),
                    currentUserId = currentUserId,
                    initialDisplayName = userDisplayName
                )
            }
        }
    )

    val profileState by appViewModel.userProfile.collectAsStateWithLifecycle()
    val roomsState by appViewModel.myRooms.collectAsStateWithLifecycle()
    val channelsState by appViewModel.liveChannels.collectAsStateWithLifecycle()
    val localHealthOverrides by appViewModel.localHealthOverrides.collectAsStateWithLifecycle()
    val healthCheckRunning by appViewModel.healthCheckRunning.collectAsStateWithLifecycle()
    val isBusy by appViewModel.busyAction.collectAsStateWithLifecycle()
    val statusBanner by appViewModel.statusBanner.collectAsStateWithLifecycle()

    val profile = (profileState as? UiState.Success)?.data ?: UserProfile(
        id = currentUserId,
        userId = currentUserId,
        displayName = SyncEngine.sanitizeDisplayName(userDisplayName),
        avatarColorHex = "#FF4D6D"
    )
    val channelsList = (channelsState as? UiState.Success)?.data.orEmpty()

    WatchTogetherTheme(darkTheme = profile.darkThemeEnabled) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HOME

        LaunchedEffect(statusBanner) {
            val msg = statusBanner
            if (!msg.isNullOrBlank()) {
                snackbarHostState.showSnackbar(msg)
                appViewModel.clearBanner()
            }
        }

        // Handle watchtogether://room/XXXXXX deep link
        LaunchedEffect(pendingRoomCode) {
            val code = pendingRoomCode
            if (!code.isNullOrBlank()) {
                onConsumePendingRoomCode()
                appViewModel.joinWatchRoom(code, profile.displayName) { joinedCode ->
                    navController.navigate("room/$joinedCode") {
                        launchSingleTop = true
                    }
                }
            }
        }

        val destinations = remember {
            listOf(
                TopDestination(ROUTE_HOME, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
                TopDestination(ROUTE_LIVE_TV, R.string.nav_live_tv, Icons.Filled.LiveTv, Icons.Outlined.LiveTv),
                TopDestination(ROUTE_SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
            )
        }
        val showChrome = !currentRoute.startsWith("room/") && !isInPipMode

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 680.dp

            CinematicBackdrop {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (showChrome && useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            modifier = Modifier.statusBarsPadding()
                        ) {
                            destinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(dest.route) {
                                            popUpTo(ROUTE_HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) }
                                )
                            }
                        }
                    }

                    Scaffold(
                        containerColor = Color.Transparent,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            if (showChrome && !useRail) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                                ) {
                                    destinations.forEach { dest ->
                                        val selected = currentRoute == dest.route
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = {
                                                navController.navigate(dest.route) {
                                                    popUpTo(ROUTE_HOME) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                    contentDescription = stringResource(dest.labelRes)
                                                )
                                            },
                                            label = { Text(stringResource(dest.labelRes)) }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_HOME,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (showChrome) Modifier.padding(innerPadding) else Modifier)
                        ) {
                            composable(ROUTE_HOME) {
                                HomeScreen(
                                    profile = profile,
                                    roomsState = roomsState,
                                    isBusy = isBusy,
                                    onCreateRoom = { title, nick ->
                                        appViewModel.createWatchRoom(
                                            title = title,
                                            nicknameOverride = nick
                                        ) { code ->
                                            navController.navigate("room/$code")
                                        }
                                    },
                                    onJoinRoom = { code, nick ->
                                        appViewModel.joinWatchRoom(code, nick) { joined ->
                                            navController.navigate("room/$joined")
                                        }
                                    },
                                    onOpenRoom = { code ->
                                        navController.navigate("room/$code")
                                    }
                                )
                            }

                            composable(ROUTE_LIVE_TV) {
                                LiveTvScreen(
                                    channelsState = channelsState,
                                    localHealthOverrides = localHealthOverrides,
                                    healthCheckRunning = healthCheckRunning,
                                    isAdmin = appViewModel.isAdmin,
                                    onWatchChannel = { channel ->
                                        appViewModel.createRoomFromChannel(channel) { code ->
                                            navController.navigate("room/$code")
                                        }
                                    },
                                    onRunHealthCheck = { appViewModel.runStreamHealthChecks() },
                                    onSeedChannels = { appViewModel.seedStarterChannels() },
                                    onSaveChannel = { id, name, cat, lang, type, url, broadcaster, color, pub ->
                                        appViewModel.saveChannel(id, name, cat, lang, type, url, broadcaster, color, pub)
                                    },
                                    onDeleteChannel = { id -> appViewModel.deleteChannel(id) }
                                )
                            }

                            composable(ROUTE_SETTINGS) {
                                SettingsScreen(
                                    profile = profile,
                                    userEmail = userEmail,
                                    isAdmin = appViewModel.isAdmin,
                                    onSaveProfile = { name, color, dark, sync, haptics, lkUrl ->
                                        appViewModel.updateProfile(name, color, dark, sync, haptics, lkUrl)
                                    },
                                    onSignOut = {
                                        signOutUser(
                                            credentialManager = credentialManager,
                                            scope = scope
                                        )
                                    }
                                )
                            }

                            composable(
                                route = ROUTE_ROOM,
                                arguments = listOf(navArgument("roomCode") { type = NavType.StringType })
                            ) { entry ->
                                val roomCode = entry.arguments?.getString("roomCode").orEmpty()
                                val roomViewModel: RoomViewModel = viewModel(
                                    key = "${currentUserId}_$roomCode",
                                    factory = viewModelFactory {
                                        initializer {
                                            val app = checkNotNull(this[APPLICATION_KEY])
                                            val databaseId = app.getString(R.string.firestore_database_id)
                                            val db = FirebaseFirestore.getInstance(databaseId)
                                            RoomViewModel(
                                                repository = WatchTogetherRepository(db),
                                                mediaLookupService = MediaLookupService(),
                                                roomCode = roomCode,
                                                currentUserId = currentUserId
                                            )
                                        }
                                    }
                                )
                                RoomScreen(
                                    viewModel = roomViewModel,
                                    profile = profile,
                                    liveChannels = channelsList,
                                    isInPipMode = isInPipMode,
                                    onEnterPip = onRequestPip,
                                    onExitRoom = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
