package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.ChannelParticipant
import com.example.model.NetworkTransport
import com.example.model.RoomType
import com.example.model.VoiceConnectionState
import com.example.model.WalkieRoom
import com.example.model.WalkieSessionSnapshot
import com.example.ui.theme.AmberBusy
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElevatedSteel
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RadioCyan
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.SignalOrangePressed
import com.example.ui.theme.SteelSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun WalkieConsoleScreen(
    sessionState: WalkieSessionSnapshot,
    networkTransport: NetworkTransport,
    backendUrl: String,
    hapticsEnabled: Boolean,
    availableRooms: List<WalkieRoom>,
    onConnectRoom: (WalkieRoom) -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onPttPressStart: () -> Unit,
    onPttPressEnd: () -> Unit,
    onToggleSpeakerphone: () -> Unit,
    onClearError: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenServerSettings: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensurePermissionsBeforeConnect(room: WalkieRoom) {
        if (!hasMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        onConnectRoom(room)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianBg, Color(0xFF0E131B), ObsidianBg)
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Brand & Network Telemetry Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SignalOrange.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Radio,
                            contentDescription = null,
                            tint = SignalOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "MY WALKIE",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (sessionState.isBackgroundServiceRunning) {
                                "BG LISTENING ACTIVE"
                            } else {
                                "VOIP PUSH-TO-TALK"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (sessionState.isBackgroundServiceRunning) RadioCyan else TextTertiary
                        )
                    }
                }

                NetworkTransportBadge(transport = networkTransport)
            }

            // Mic Permission Card if missing
            if (!hasMicPermission) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = AmberBusy.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, AmberBusy.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MicOff,
                            contentDescription = null,
                            tint = AmberBusy
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Microphone Access Needed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Grant microphone permission to transmit real-time LiveKit audio.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Button(
                            onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberBusy,
                                contentColor = ObsidianBg
                            ),
                            modifier = Modifier.testTag("grant_mic_button")
                        ) {
                            Text("Allow")
                        }
                    }
                }
            }

            // Backend URL Setup Banner if empty
            if (backendUrl.isBlank()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = CarbonSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RadioCyan.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Voice Backend Not Configured",
                            style = MaterialTheme.typography.titleMedium,
                            color = RadioCyan
                        )
                        Text(
                            text = "Deploy the included LiveKit Token & Floor Control backend service and enter its URL in Profile -> Server Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        FilledTonalButton(
                            onClick = onOpenServerSettings,
                            modifier = Modifier.testTag("configure_backend_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Configure Server URL")
                        }
                    }
                }
            }

            // Active Channel Card
            val activeRoom = sessionState.activeRoom
            if (activeRoom != null) {
                ActiveChannelTelemetryCard(
                    room = activeRoom,
                    sessionState = sessionState,
                    onReconnect = onReconnect,
                    onDisconnect = onDisconnect,
                    onCopyCode = { code ->
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Channel Code", code))
                    }
                )
            } else {
                StandbyChannelSelectorCard(
                    rooms = availableRooms,
                    onSelectRoom = { ensurePermissionsBeforeConnect(it) },
                    onOpenChannels = onOpenChannels
                )
            }

            // Error / Busy Floor Alert
            val alertText = sessionState.floorState.busyMessage ?: sessionState.errorMessage
            if (!alertText.isNullOrBlank()) {
                val isBusy = sessionState.floorState.busyMessage != null
                val alertColor = if (isBusy) AmberBusy else CrimsonAlert
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = alertColor.copy(alpha = 0.14f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, alertColor.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = alertText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isBusy && activeRoom != null) {
                            OutlinedButton(
                                onClick = onReconnect,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("error_reconnect_button")
                            ) {
                                Text("Retry", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        IconButton(onClick = onClearError) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss alert",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            // Live Speaker & Waveform Stage
            SpeakerMonitorCard(sessionState = sessionState)

            // Large Push-to-Talk Tactical Button
            PushToTalkConsoleButton(
                sessionState = sessionState,
                hasMicPermission = hasMicPermission,
                onRequestMicPermission = {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onPressStart = {
                    if (hapticsEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    onPttPressStart()
                },
                onPressEnd = {
                    if (hapticsEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onPttPressEnd()
                }
            )

            // Bottom Audio Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (sessionState.isSpeakerphoneEnabled) {
                        RadioCyan.copy(alpha = 0.14f)
                    } else {
                        CarbonSurface
                    },
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            1.dp,
                            if (sessionState.isSpeakerphoneEnabled) RadioCyan.copy(alpha = 0.45f) else BorderSubtle,
                            RoundedCornerShape(16.dp)
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onToggleSpeakerphone)
                        .testTag("speaker_toggle_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Toggle speakerphone",
                            tint = if (sessionState.isSpeakerphoneEnabled) RadioCyan else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (sessionState.isSpeakerphoneEnabled) "Speaker ON" else "Earpiece",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (sessionState.isSpeakerphoneEnabled) RadioCyan else TextSecondary
                        )
                    }
                }

                if (activeRoom != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CarbonSurface,
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onReconnect)
                            .testTag("reconnect_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Reconnect channel",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reconnect",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Live Channel Participants Roster
            if (sessionState.participants.isNotEmpty()) {
                ParticipantRosterStrip(participants = sessionState.participants)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun NetworkTransportBadge(transport: NetworkTransport) {
    val (color, icon) = when (transport) {
        NetworkTransport.WIFI -> EmeraldOnline to Icons.Rounded.Wifi
        NetworkTransport.CELLULAR -> RadioCyan to Icons.Rounded.SignalCellularAlt
        NetworkTransport.ETHERNET -> EmeraldOnline to Icons.Rounded.Wifi
        NetworkTransport.OFFLINE -> CrimsonAlert to Icons.Rounded.WifiOff
        NetworkTransport.UNKNOWN -> TextSecondary to Icons.Rounded.Wifi
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = transport.label,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = transport.label,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

@Composable
private fun ActiveChannelTelemetryCard(
    room: WalkieRoom,
    sessionState: WalkieSessionSnapshot,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    val statusColor = when (sessionState.connectionState) {
        VoiceConnectionState.CONNECTED -> EmeraldOnline
        VoiceConnectionState.FETCHING_TOKEN,
        VoiceConnectionState.CONNECTING,
        VoiceConnectionState.RECONNECTING -> AmberBusy
        VoiceConnectionState.ERROR -> CrimsonAlert
        VoiceConnectionState.DISCONNECTED -> TextTertiary
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CarbonSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (room.parsedType == RoomType.PRIVATE) {
                            Icons.Rounded.Person
                        } else {
                            Icons.Rounded.Groups
                        },
                        contentDescription = null,
                        tint = RadioCyan
                    )
                    Column {
                        Text(
                            text = room.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = room.parsedType.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.16f),
                    modifier = Modifier.border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = sessionState.connectionState.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (room.parsedType == RoomType.GROUP) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SteelSurface,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onCopyCode(room.joinCode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "CODE: ${room.joinCode}",
                                style = MaterialTheme.typography.labelMedium,
                                color = RadioCyan
                            )
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy channel code",
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Encrypted 1-on-1 LiveKit Channel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessionState.connectionState == VoiceConnectionState.ERROR ||
                        sessionState.connectionState == VoiceConnectionState.DISCONNECTED
                    ) {
                        FilledTonalButton(
                            onClick = onReconnect,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Reconnect", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("disconnect_channel_button")
                    ) {
                        Text("Leave", style = MaterialTheme.typography.labelMedium, color = CrimsonAlert)
                    }
                }
            }
        }
    }
}

@Composable
private fun StandbyChannelSelectorCard(
    rooms: List<WalkieRoom>,
    onSelectRoom: (WalkieRoom) -> Unit,
    onOpenChannels: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CarbonSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "No Active Channel",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select a channel below or create/join one to start talking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            if (rooms.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(rooms.take(6), key = { it.id }) { room ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SteelSurface,
                            modifier = Modifier
                                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectRoom(room) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (room.parsedType == RoomType.PRIVATE) {
                                        Icons.Rounded.PhoneInTalk
                                    } else {
                                        Icons.Rounded.Groups
                                    },
                                    contentDescription = null,
                                    tint = SignalOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = room.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                Button(
                    onClick = onOpenChannels,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalOrange,
                        contentColor = ObsidianBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Browse or Create Channels")
                }
            }
        }
    }
}

@Composable
private fun SpeakerMonitorCard(sessionState: WalkieSessionSnapshot) {
    val floor = sessionState.floorState
    val isConnected = sessionState.connectionState == VoiceConnectionState.CONNECTED
    val activeLevel = when {
        floor.isHeldByMe -> maxOf(sessionState.localAudioLevel, 0.35f)
        floor.isSomeoneElseSpeaking -> maxOf(sessionState.remoteAudioLevel, 0.3f)
        else -> 0.05f
    }

    val bannerColor by animateColorAsState(
        targetValue = when {
            floor.isHeldByMe -> SignalOrange
            floor.isSomeoneElseSpeaking -> RadioCyan
            isConnected -> EmeraldOnline
            else -> TextTertiary
        },
        label = "speakerBannerColor"
    )

    val speakerTitle = when {
        !isConnected -> "Standby — Channel Offline"
        floor.isHeldByMe -> "YOU ARE ON AIR"
        !floor.speakerName.isNullOrBlank() -> "ON AIR: ${floor.speakerName}"
        else -> "FLOOR OPEN"
    }

    val speakerSubtitle = when {
        !isConnected -> "Connect to a Private or Group channel to listen and speak"
        floor.isHeldByMe -> "Server floor locked to you • Release button to stop"
        !floor.speakerName.isNullOrBlank() -> "Listening to incoming LiveKit audio stream"
        else -> "Hold the Push-to-Talk button below to claim the floor"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SteelSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, bannerColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when {
                            floor.isHeldByMe -> Icons.Rounded.Mic
                            floor.isSomeoneElseSpeaking -> Icons.Rounded.Hearing
                            else -> Icons.Rounded.GraphicEq
                        },
                        contentDescription = null,
                        tint = bannerColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = speakerTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = bannerColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = speakerSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                if (floor.isSomeoneElseSpeaking || floor.isHeldByMe) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Server floor locked",
                        tint = bannerColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AudioSpectrumBar(
                active = floor.isHeldByMe || floor.isSomeoneElseSpeaking,
                level = activeLevel,
                barColor = bannerColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            )
        }
    }
}

@Composable
private fun AudioSpectrumBar(
    active: Boolean,
    level: Float,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "spectrum")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spectrumPhase"
    )

    Canvas(modifier = modifier) {
        val barCount = 24
        val gap = 6.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(3f)
        for (i in 0 until barCount) {
            val wave = if (active) {
                val norm = kotlin.math.abs(kotlin.math.sin(phase + i * 0.45f))
                (0.25f + 0.75f * norm * level.coerceIn(0.2f, 1f))
            } else {
                0.14f
            }
            val barHeight = (size.height * wave).coerceAtLeast(4.dp.toPx())
            val x = i * (barWidth + gap)
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = barColor.copy(alpha = if (active) 0.9f else 0.25f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun PushToTalkConsoleButton(
    sessionState: WalkieSessionSnapshot,
    hasMicPermission: Boolean,
    onRequestMicPermission: () -> Unit,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    val isConnected = sessionState.connectionState == VoiceConnectionState.CONNECTED
    val floor = sessionState.floorState
    val isTransmitting = floor.isHeldByMe
    val isRequesting = floor.isRequesting
    val isBlockedByOther = floor.isSomeoneElseSpeaking

    val scale by animateFloatAsState(
        targetValue = if (isTransmitting || isRequesting) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "pttScale"
    )

    val transition = rememberInfiniteTransition(label = "pttPulse")
    val pulseProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    val coreColor by animateColorAsState(
        targetValue = when {
            !isConnected || !hasMicPermission -> ElevatedSteel
            isTransmitting -> SignalOrange
            isRequesting -> SignalOrangePressed
            isBlockedByOther -> AmberBusy.copy(alpha = 0.25f)
            else -> CarbonSurface
        },
        label = "pttCoreColor"
    )

    val ringColor = when {
        isTransmitting -> SignalOrange
        isBlockedByOther -> AmberBusy
        isConnected -> RadioCyan
        else -> BorderSubtle
    }

    val buttonLabel = when {
        !hasMicPermission -> "TAP TO GRANT MIC"
        !isConnected -> "CONNECT A CHANNEL"
        isRequesting -> "ACQUIRING FLOOR…"
        isTransmitting -> "TRANSMITTING"
        isBlockedByOther -> "FLOOR LOCKED"
        else -> "HOLD TO TALK"
    }

    val buttonHint = when {
        !hasMicPermission -> "Microphone permission needed"
        !isConnected -> "Select a room above"
        isTransmitting -> "Release to stop"
        isBlockedByOther -> "${floor.speakerName ?: "Operator"} is speaking"
        else -> "Push & hold to speak"
    }

    Box(
        modifier = Modifier
            .size(248.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Outer Animated Radio Wave Rings
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.38f

            if (isTransmitting || floor.isSomeoneElseSpeaking) {
                val activeWaveColor = if (isTransmitting) SignalOrange else RadioCyan
                for (wave in 0..1) {
                    val phase = (pulseProgress + wave * 0.5f) % 1f
                    val radius = baseRadius + (size.minDimension * 0.12f) * phase
                    drawCircle(
                        color = activeWaveColor.copy(alpha = (1f - phase) * 0.55f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }

            drawCircle(
                color = ringColor.copy(alpha = if (isConnected) 0.55f else 0.25f),
                radius = size.minDimension * 0.44f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Tactile Hold-to-Talk Surface
        Box(
            modifier = Modifier
                .size(188.dp)
                .clip(CircleShape)
                .background(
                    brush = if (isTransmitting) {
                        Brush.radialGradient(listOf(SignalOrange, SignalOrangePressed))
                    } else {
                        Brush.radialGradient(listOf(coreColor, ObsidianBg))
                    }
                )
                .border(
                    width = 3.dp,
                    color = ringColor,
                    shape = CircleShape
                )
                .pointerInput(isConnected, hasMicPermission, isBlockedByOther) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (!hasMicPermission) {
                            waitForUpOrCancellation()
                            onRequestMicPermission()
                        } else if (isConnected) {
                            onPressStart()
                            waitForUpOrCancellation()
                            onPressEnd()
                        } else {
                            waitForUpOrCancellation()
                        }
                    }
                }
                .testTag("ptt_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                if (isRequesting || sessionState.connectionState == VoiceConnectionState.CONNECTING ||
                    sessionState.connectionState == VoiceConnectionState.FETCHING_TOKEN
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = if (isRequesting) ObsidianBg else RadioCyan,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (hasMicPermission) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                        contentDescription = buttonLabel,
                        tint = when {
                            isTransmitting -> ObsidianBg
                            isBlockedByOther -> AmberBusy
                            isConnected -> SignalOrange
                            else -> TextSecondary
                        },
                        modifier = Modifier.size(48.dp)
                    )
                }
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isTransmitting) ObsidianBg else MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = buttonHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isTransmitting) ObsidianBg.copy(alpha = 0.85f) else TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ParticipantRosterStrip(participants: List<ChannelParticipant>) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CarbonSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ACTIVE ON FREQUENCY (${participants.size})",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(participants, key = { it.uid }) { participant ->
                    val highlight = when {
                        participant.isSpeaking && participant.isLocal -> SignalOrange
                        participant.isSpeaking -> RadioCyan
                        else -> BorderSubtle
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SteelSurface,
                        modifier = Modifier.border(1.dp, highlight, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (participant.isSpeaking) highlight else EmeraldOnline)
                            )
                            Text(
                                text = if (participant.isLocal) "${participant.name} (You)" else participant.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = participant.qualityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
