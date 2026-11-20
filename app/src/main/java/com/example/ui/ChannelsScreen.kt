package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.RoomType
import com.example.model.UiState
import com.example.model.WalkieRoom
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

private enum class ChannelFilter(val label: String) {
    ALL("All"),
    GROUP("Groups"),
    PRIVATE("Private")
}

@Composable
fun ChannelsScreen(
    roomsState: UiState<List<WalkieRoom>>,
    activeRoomId: String?,
    currentUserId: String,
    onConnectRoom: (WalkieRoom) -> Unit,
    onCreateGroupRoom: (name: String, description: String, maxParticipants: Int) -> Unit,
    onJoinByCode: (String) -> Unit,
    onLeaveGroupRoom: (WalkieRoom) -> Unit,
    onDeleteRoom: (WalkieRoom) -> Unit
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf(ChannelFilter.ALL) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

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
                text = "Channels",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalOrange,
                        contentColor = ObsidianBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("create_channel_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Group")
                }

                OutlinedButton(
                    onClick = { showJoinDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("join_code_button")
                ) {
                    Icon(Icons.Rounded.Key, contentDescription = null, tint = RadioCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Join Code", color = RadioCyan)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChannelFilter.entries.forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SignalOrange.copy(alpha = 0.2f),
                            selectedLabelColor = SignalOrange
                        )
                    )
                }
            }

            when (roomsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
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
                            text = roomsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is UiState.Success -> {
                    val filtered = remember(roomsState.data, filter) {
                        when (filter) {
                            ChannelFilter.ALL -> roomsState.data
                            ChannelFilter.GROUP -> roomsState.data.filter { it.parsedType == RoomType.GROUP }
                            ChannelFilter.PRIVATE -> roomsState.data.filter { it.parsedType == RoomType.PRIVATE }
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
                                    imageVector = Icons.Rounded.Radio,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "No Walkie Channels Yet",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Create a Group Talk channel, enter an invite code, or start a 1-on-1 Private Talk from Contacts.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(filtered, key = { it.id }) { room ->
                                ChannelCard(
                                    room = room,
                                    isActive = room.id == activeRoomId,
                                    isOwner = room.ownerId == currentUserId,
                                    onConnect = { onConnectRoom(room) },
                                    onCopyCode = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Channel Code", room.joinCode))
                                    },
                                    onLeave = { onLeaveGroupRoom(room) },
                                    onDelete = { onDeleteRoom(room) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc, max ->
                showCreateDialog = false
                onCreateGroupRoom(name, desc, max)
            }
        )
    }

    if (showJoinDialog) {
        JoinByCodeDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { code ->
                showJoinDialog = false
                onJoinByCode(code)
            }
        )
    }
}

@Composable
private fun ChannelCard(
    room: WalkieRoom,
    isActive: Boolean,
    isOwner: Boolean,
    onConnect: () -> Unit,
    onCopyCode: () -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isActive) SignalOrange else BorderSubtle
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = CarbonSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onConnect)
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (room.parsedType == RoomType.PRIVATE) {
                                    RadioCyan.copy(alpha = 0.14f)
                                } else {
                                    SignalOrange.copy(alpha = 0.16f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (room.parsedType == RoomType.PRIVATE) {
                                Icons.Rounded.Person
                            } else {
                                Icons.Rounded.Groups
                            },
                            contentDescription = null,
                            tint = if (room.parsedType == RoomType.PRIVATE) RadioCyan else SignalOrange
                        )
                    }
                    Column {
                        Text(
                            text = room.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${room.parsedType.label} • ${room.memberUids.size}/${room.maxParticipants} members",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = EmeraldOnline.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldOnline)
                            )
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldOnline
                            )
                        }
                    }
                }
            }

            if (!room.description.isNullOrBlank()) {
                Text(
                    text = room.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!room.activeSpeakerName.isNullOrBlank()) {
                Text(
                    text = "Speaking now: ${room.activeSpeakerName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = SignalOrange
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (room.parsedType == RoomType.GROUP) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = SteelSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onCopyCode)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = room.joinCode,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = RadioCyan
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy invite code",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (isOwner) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete channel",
                                tint = TextTertiary
                            )
                        }
                    } else if (room.parsedType == RoomType.GROUP) {
                        IconButton(onClick = onLeave) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                                contentDescription = "Leave channel",
                                tint = TextTertiary
                            )
                        }
                    }
                }

                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) SteelSurface else SignalOrange,
                        contentColor = if (isActive) RadioCyan else ObsidianBg
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("connect_channel_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isActive) "Open" else "Tune In")
                }
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, maxParticipants: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var maxSlider by remember { mutableFloatStateOf(20f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        title = { Text("Create Group Channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("Channel Name") },
                    placeholder = { Text("e.g. Ops Alpha") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_room_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 160) description = it },
                    label = { Text("Description (optional)") },
                    placeholder = { Text("Team coordination channel") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    Text(
                        text = "Max Participants: ${maxSlider.toInt()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = RadioCyan
                    )
                    Slider(
                        value = maxSlider,
                        onValueChange = { maxSlider = it },
                        valueRange = 2f..50f
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, maxSlider.toInt()) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_create_room_button")
            ) {
                Text("Create & Tune In")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun JoinByCodeDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        title = { Text("Join Channel by Code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the 6-character channel code shared by the host.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 12) code = it.uppercase() },
                    label = { Text("Channel Code") },
                    placeholder = { Text("e.g. WK4829") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code) },
                enabled = code.trim().length >= 4,
                modifier = Modifier.testTag("confirm_join_code_button")
            ) {
                Text("Join & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
