package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.model.UiState
import com.example.model.VoiceConnectionState
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RadioCyan
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.SteelSurface
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.WalkieMainViewModel
import kotlinx.serialization.Serializable

@Serializable
sealed interface WalkieRoute {
    @Serializable
    data object Console : WalkieRoute

    @Serializable
    data object Channels : WalkieRoute

    @Serializable
    data object Contacts : WalkieRoute

    @Serializable
    data object Profile : WalkieRoute
}

private data class NavTab(
    val route: WalkieRoute,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val WALKIE_TABS = listOf(
    NavTab(WalkieRoute.Console, "Walkie", Icons.Rounded.Radio, "nav_console"),
    NavTab(WalkieRoute.Channels, "Channels", Icons.Rounded.Groups, "nav_channels"),
    NavTab(WalkieRoute.Contacts, "Direct", Icons.Rounded.PhoneInTalk, "nav_contacts"),
    NavTab(WalkieRoute.Profile, "Profile", Icons.Rounded.Person, "nav_profile")
)

@Composable
fun MainWalkieScaffold(
    viewModel: WalkieMainViewModel,
    currentUserId: String
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }

    val profileState by viewModel.myProfileState.collectAsStateWithLifecycle()
    val privateSettings by viewModel.myPrivateSettings.collectAsStateWithLifecycle()
    val roomsState by viewModel.roomsState.collectAsStateWithLifecycle()
    val contactsState by viewModel.contactsState.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val networkTransport by viewModel.networkTransport.collectAsStateWithLifecycle()
    val backendUrl by viewModel.backendUrl.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val backendTestState by viewModel.backendTestState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.feedback.collect { item ->
            snackbarHostState.showSnackbar(item.message)
        }
    }

    fun navigateTab(route: WalkieRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isConsoleSelected = currentDestination?.hasRoute(WalkieRoute.Console::class) == true

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(ObsidianBg)) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = SteelSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            bottomBar = {
                if (!useNavigationRail) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CarbonSurface)
                            .navigationBarsPadding()
                    ) {
                        if (!isConsoleSelected && sessionState.activeRoom != null) {
                            ActiveChannelMiniBar(
                                roomName = sessionState.activeRoom!!.name,
                                connectionState = sessionState.connectionState,
                                speakerName = sessionState.floorState.speakerName,
                                isMeSpeaking = sessionState.floorState.isHeldByMe,
                                onClick = { navigateTab(WalkieRoute.Console) }
                            )
                        }
                        NavigationBar(
                            containerColor = CarbonSurface,
                            tonalElevation = 0.dp
                        ) {
                            WALKIE_TABS.forEach { tab ->
                                val selected = currentDestination?.hasRoute(tab.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SignalOrange,
                                        selectedTextColor = SignalOrange,
                                        indicatorColor = SignalOrange.copy(alpha = 0.16f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = CarbonSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(0.2f))
                        WALKIE_TABS.forEach { tab ->
                            val selected = currentDestination?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = SignalOrange,
                                    selectedTextColor = SignalOrange,
                                    indicatorColor = SignalOrange.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = WalkieRoute.Console
                    ) {
                        composable<WalkieRoute.Console> {
                            val rooms = (roomsState as? UiState.Success)?.data.orEmpty()
                            WalkieConsoleScreen(
                                sessionState = sessionState,
                                networkTransport = networkTransport,
                                backendUrl = backendUrl.ifBlank {
                                    privateSettings?.backendUrl.orEmpty()
                                },
                                hapticsEnabled = hapticsEnabled,
                                availableRooms = rooms,
                                onConnectRoom = { viewModel.connectToRoom(it) },
                                onReconnect = { viewModel.reconnectChannel() },
                                onDisconnect = { viewModel.disconnectChannel() },
                                onPttPressStart = { viewModel.onPttPressStart() },
                                onPttPressEnd = { viewModel.onPttPressEnd() },
                                onToggleSpeakerphone = { viewModel.toggleSpeakerphone() },
                                onClearError = { viewModel.clearSessionError() },
                                onOpenChannels = { navigateTab(WalkieRoute.Channels) },
                                onOpenServerSettings = { navigateTab(WalkieRoute.Profile) }
                            )
                        }

                        composable<WalkieRoute.Channels> {
                            ChannelsScreen(
                                roomsState = roomsState,
                                activeRoomId = sessionState.activeRoom?.id,
                                currentUserId = currentUserId,
                                onConnectRoom = { room ->
                                    viewModel.connectToRoom(room)
                                    navigateTab(WalkieRoute.Console)
                                },
                                onCreateGroupRoom = { name, desc, max ->
                                    viewModel.createGroupRoom(name, desc, max, autoConnect = true) {
                                        navigateTab(WalkieRoute.Console)
                                    }
                                },
                                onJoinByCode = { code ->
                                    viewModel.joinGroupByCode(code, autoConnect = true) {
                                        navigateTab(WalkieRoute.Console)
                                    }
                                },
                                onLeaveGroupRoom = { viewModel.leaveGroupRoom(it) },
                                onDeleteRoom = { viewModel.deleteRoom(it) }
                            )
                        }

                        composable<WalkieRoute.Contacts> {
                            ContactsScreen(
                                contactsState = contactsState,
                                onStartPrivateTalk = { target ->
                                    viewModel.startPrivateTalk(target) {
                                        navigateTab(WalkieRoute.Console)
                                    }
                                }
                            )
                        }

                        composable<WalkieRoute.Profile> {
                            ProfileSettingsScreen(
                                profileState = profileState,
                                privateSettings = privateSettings,
                                backendUrl = backendUrl,
                                hapticsEnabled = hapticsEnabled,
                                backendTestState = backendTestState,
                                onUpdateOnlineStatus = { viewModel.updateOnlineStatus(it) },
                                onSaveProfile = { name, callsign, status, discoverable ->
                                    viewModel.updateProfile(name, callsign, status, discoverable)
                                },
                                onSaveSettings = { url, haptics, autoAudio ->
                                    viewModel.saveSettings(url, haptics, autoAudio)
                                },
                                onTestBackendConnection = { url ->
                                    viewModel.testBackendConnection(url)
                                },
                                onClearBackendTest = {
                                    viewModel.clearBackendTestState()
                                },
                                onBeforeSignOut = {
                                    viewModel.disconnectChannel()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveChannelMiniBar(
    roomName: String,
    connectionState: VoiceConnectionState,
    speakerName: String?,
    isMeSpeaking: Boolean,
    onClick: () -> Unit
) {
    val dotColor = when {
        isMeSpeaking -> SignalOrange
        !speakerName.isNullOrBlank() -> RadioCyan
        connectionState == VoiceConnectionState.CONNECTED -> EmeraldOnline
        connectionState == VoiceConnectionState.ERROR -> CrimsonAlert
        else -> TextSecondary
    }
    Surface(
        color = SteelSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = roomName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = when {
                    isMeSpeaking -> "ON AIR"
                    !speakerName.isNullOrBlank() -> speakerName
                    else -> connectionState.label
                },
                style = MaterialTheme.typography.labelMedium,
                color = dotColor
            )
        }
    }
}
