package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.normalizeBackendBaseUrl
import com.example.model.OnlineStatus
import com.example.model.PublicProfile
import com.example.model.UiState
import com.example.model.UserPrivate
import com.example.ui.theme.AmberBusy
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RadioCyan
import com.example.ui.theme.SignalOrange
import com.example.ui.theme.SteelSurface
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.viewmodel.BackendTestState

@Composable
fun ProfileSettingsScreen(
    profileState: UiState<PublicProfile?>,
    privateSettings: UserPrivate?,
    backendUrl: String,
    hapticsEnabled: Boolean,
    backendTestState: BackendTestState,
    onUpdateOnlineStatus: (OnlineStatus) -> Unit,
    onSaveProfile: (displayName: String, callsign: String, statusMessage: String, discoverable: Boolean) -> Unit,
    onSaveSettings: (backendUrl: String, haptics: Boolean, autoJoinAudio: Boolean) -> Unit,
    onTestBackendConnection: (backendUrl: String) -> Unit,
    onClearBackendTest: () -> Unit,
    onBeforeSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val profile = (profileState as? UiState.Success)?.data

    var displayName by remember { mutableStateOf("") }
    var callsign by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var discoverable by remember { mutableStateOf(true) }

    var serverUrlInput by remember { mutableStateOf("") }
    var hapticsToggle by remember { mutableStateOf(true) }
    var autoJoinToggle by remember { mutableStateOf(true) }

    LaunchedEffect(profile) {
        if (profile != null) {
            displayName = profile.displayName
            callsign = profile.callsign
            statusMessage = profile.statusMessage
            discoverable = profile.discoverable
        }
    }

    LaunchedEffect(backendUrl, hapticsEnabled, privateSettings) {
        val effective = backendUrl.ifBlank { privateSettings?.backendUrl.orEmpty() }
        if (effective.isNotBlank() || serverUrlInput.isBlank()) {
            serverUrlInput = effective
        }
        hapticsToggle = privateSettings?.hapticsEnabled ?: hapticsEnabled
        autoJoinToggle = privateSettings?.autoJoinAudio ?: true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Operator Profile",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Identity & Online Status Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CarbonSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(SignalOrange.copy(alpha = 0.2f))
                                .border(2.dp, SignalOrange, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile?.initials ?: "WK",
                                style = MaterialTheme.typography.titleLarge,
                                color = SignalOrange
                            )
                        }
                        Column {
                            Text(
                                text = profile?.displayName ?: "Loading…",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = privateSettings?.email ?: profile?.callsign.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Text(
                        text = "AVAILABILITY STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(OnlineStatus.ONLINE, OnlineStatus.BUSY, OnlineStatus.OFFLINE).forEach { status ->
                            val selected = profile?.parsedStatus == status
                            val color = when (status) {
                                OnlineStatus.ONLINE -> EmeraldOnline
                                OnlineStatus.BUSY -> AmberBusy
                                else -> TextTertiary
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { onUpdateOnlineStatus(status) },
                                label = { Text(status.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = color.copy(alpha = 0.2f),
                                    selectedLabelColor = color
                                )
                            )
                        }
                    }

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { if (it.length <= 50) displayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = callsign,
                        onValueChange = { if (it.length <= 24) callsign = it.uppercase() },
                        label = { Text("Radio Callsign") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = statusMessage,
                        onValueChange = { if (it.length <= 120) statusMessage = it },
                        label = { Text("Status Message") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Discoverable in Directory",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Allow other signed-in users to start 1-on-1 Private Talk",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = discoverable,
                            onCheckedChange = { discoverable = it }
                        )
                    }

                    Button(
                        onClick = {
                            onSaveProfile(displayName, callsign, statusMessage, discoverable)
                        },
                        enabled = displayName.isNotBlank() && callsign.trim().length >= 2,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SignalOrange,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_profile_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Profile")
                    }
                }
            }

            // Server Endpoint & Audio Preferences Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CarbonSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Dns, contentDescription = null, tint = RadioCyan)
                        Text(
                            text = "Voice Backend & Audio",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "LiveKit API keys and floor-control locks stay on your Render backend (never inside this APK). Enter only your root URL — e.g. https://my-walkie-backend.onrender.com",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = {
                            serverUrlInput = it
                            if (backendTestState != BackendTestState.Idle) {
                                onClearBackendTest()
                            }
                        },
                        label = { Text("Token & Floor Backend URL") },
                        placeholder = { Text("https://my-walkie-backend.onrender.com") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backend_url_input")
                    )

                    OutlinedButton(
                        onClick = {
                            val normalized = normalizeBackendBaseUrl(serverUrlInput)
                            if (normalized.isNotBlank()) {
                                serverUrlInput = normalized
                            }
                            onTestBackendConnection(serverUrlInput)
                        },
                        enabled = backendTestState !is BackendTestState.Testing && serverUrlInput.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_backend_button")
                    ) {
                        if (backendTestState is BackendTestState.Testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = RadioCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing /health (Render may take ~25s)…")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.NetworkCheck,
                                contentDescription = null,
                                tint = RadioCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Backend Connection", color = RadioCyan)
                        }
                    }

                    when (backendTestState) {
                        is BackendTestState.Success -> {
                            val livekitReady = backendTestState.health.livekitConfigured
                            val statusColor = if (livekitReady) EmeraldOnline else AmberBusy
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = statusColor.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (livekitReady) {
                                            "Connected to ${backendTestState.normalizedUrl}/health • LiveKit & Firebase ready"
                                        } else {
                                            "Reached ${backendTestState.normalizedUrl}/health, but LIVEKIT_URL / API keys are missing in Render Environment!"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        is BackendTestState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CrimsonAlert.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CrimsonAlert.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = CrimsonAlert,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = backendTestState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        else -> Unit
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PTT Haptic Feedback", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Vibrate subtly when pressing and releasing PTT",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = hapticsToggle,
                            onCheckedChange = { hapticsToggle = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Enable Audio Output", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Listen immediately upon tuning into a channel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = autoJoinToggle,
                            onCheckedChange = { autoJoinToggle = it }
                        )
                    }

                    Button(
                        onClick = {
                            val normalized = normalizeBackendBaseUrl(serverUrlInput)
                            serverUrlInput = normalized
                            onSaveSettings(normalized, hapticsToggle, autoJoinToggle)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SteelSurface,
                            contentColor = RadioCyan
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_settings_button")
                    ) {
                        Text("Save Server & Audio Settings")
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    signOutWalkie(
                        credentialManager = credentialManager,
                        scope = scope,
                        onBeforeSignOut = onBeforeSignOut
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = null,
                    tint = CrimsonAlert,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out", color = CrimsonAlert)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
