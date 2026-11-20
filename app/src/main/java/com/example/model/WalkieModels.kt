package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class OnlineStatus(val label: String) {
    ONLINE("Online"),
    IN_CHANNEL("In Channel"),
    BUSY("Busy"),
    OFFLINE("Offline");

    companion object {
        fun fromRaw(raw: String?): OnlineStatus {
            return entries.firstOrNull { it.name == raw } ?: ONLINE
        }
    }
}

enum class RoomType(val label: String) {
    PRIVATE("Private Talk"),
    GROUP("Group Talk");

    companion object {
        fun fromRaw(raw: String?): RoomType {
            return entries.firstOrNull { it.name == raw } ?: GROUP
        }
    }
}

@Keep
@IgnoreExtraProperties
data class UserPrivate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String? = null,
    val hapticsEnabled: Boolean = true,
    val autoJoinAudio: Boolean = true,
    val backendUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val callsign: String = "",
    val statusMessage: String = "",
    val avatarColorHex: String? = null,
    val photoUrl: String? = null,
    val onlineStatus: String = OnlineStatus.ONLINE.name,
    val activeRoomId: String? = null,
    val discoverable: Boolean = true,
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedStatus: OnlineStatus
        get() = OnlineStatus.fromRaw(onlineStatus)

    val initials: String
        get() {
            val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            return when {
                parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
                parts.size == 1 -> parts[0].take(2).uppercase()
                callsign.isNotBlank() -> callsign.take(2).uppercase()
                else -> "WK"
            }
        }
}

@Keep
@IgnoreExtraProperties
data class WalkieRoom(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String? = null,
    val roomType: String = RoomType.GROUP.name,
    val joinCode: String = "",
    val livekitRoomName: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val maxParticipants: Long = 20L,
    val activeSpeakerId: String? = null,
    val activeSpeakerName: String? = null,
    @ServerTimestamp val floorUpdatedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedType: RoomType
        get() = RoomType.fromRaw(roomType)
}

@Keep
@IgnoreExtraProperties
data class RoomInvite(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val roomName: String = "",
    val ownerId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class VoiceConnectionState(val label: String) {
    DISCONNECTED("Disconnected"),
    FETCHING_TOKEN("Authorizing…"),
    CONNECTING("Connecting…"),
    CONNECTED("Live"),
    RECONNECTING("Reconnecting…"),
    ERROR("Connection Error")
}

enum class NetworkTransport(val label: String) {
    WIFI("Wi-Fi"),
    CELLULAR("4G / 5G"),
    ETHERNET("Ethernet"),
    OFFLINE("Offline"),
    UNKNOWN("Connected")
}

data class FloorState(
    val isHeldByMe: Boolean = false,
    val isRequesting: Boolean = false,
    val speakerUid: String? = null,
    val speakerName: String? = null,
    val leaseToken: String? = null,
    val leaseExpiresAtMs: Long = 0L,
    val busyMessage: String? = null
) {
    val isSomeoneElseSpeaking: Boolean
        get() = !isHeldByMe && !speakerUid.isNullOrBlank()
}

data class ChannelParticipant(
    val uid: String,
    val name: String,
    val isLocal: Boolean,
    val isSpeaking: Boolean,
    val isMicrophoneEnabled: Boolean,
    val qualityLabel: String = "HD"
)

data class WalkieSessionSnapshot(
    val activeRoom: WalkieRoom? = null,
    val connectionState: VoiceConnectionState = VoiceConnectionState.DISCONNECTED,
    val floorState: FloorState = FloorState(),
    val participants: List<ChannelParticipant> = emptyList(),
    val isSpeakerphoneEnabled: Boolean = true,
    val isBackgroundServiceRunning: Boolean = false,
    val errorMessage: String? = null,
    val localAudioLevel: Float = 0f,
    val remoteAudioLevel: Float = 0f
)
