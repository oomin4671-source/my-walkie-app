package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.OnlineStatus
import com.example.model.PublicProfile
import com.example.model.UiState
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

@Composable
fun ContactsScreen(
    contactsState: UiState<List<PublicProfile>>,
    onStartPrivateTalk: (PublicProfile) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Private Talk",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Tap any operator to open an instant 1-on-1 LiveKit channel.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search callsign or name") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = TextSecondary)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contacts_search_input")
            )

            when (contactsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SignalOrange)
                    }
                }

                is UiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CrimsonAlert.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = contactsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    val query = searchQuery.trim().lowercase()
                    val filtered = remember(contactsState.data, query) {
                        if (query.isEmpty()) contactsState.data
                        else contactsState.data.filter {
                            it.displayName.lowercase().contains(query) ||
                                it.callsign.lowercase().contains(query)
                        }
                    }

                    if (filtered.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = CarbonSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonSearch,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "No Other Operators Online",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Sign in on a second Android device with another Google account to see operators here and start a 1-on-1 Private Talk.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(filtered, key = { it.userId }) { profile ->
                                OperatorProfileCard(
                                    profile = profile,
                                    onPrivateTalk = { onStartPrivateTalk(profile) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperatorProfileCard(
    profile: PublicProfile,
    onPrivateTalk: () -> Unit
) {
    val statusColor = when (profile.parsedStatus) {
        OnlineStatus.ONLINE -> EmeraldOnline
        OnlineStatus.IN_CHANNEL -> RadioCyan
        OnlineStatus.BUSY -> AmberBusy
        OnlineStatus.OFFLINE -> TextTertiary
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CarbonSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SteelSurface)
                        .border(1.5.dp, statusColor.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(ObsidianBg)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SteelSurface
                    ) {
                        Text(
                            text = profile.callsign,
                            style = MaterialTheme.typography.labelSmall,
                            color = RadioCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${profile.parsedStatus.label} • ${profile.statusMessage.ifBlank { "Available" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onPrivateTalk,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SignalOrange,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("private_talk_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhoneInTalk,
                    contentDescription = "Private talk with ${profile.displayName}",
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Talk")
            }
        }
    }
}
