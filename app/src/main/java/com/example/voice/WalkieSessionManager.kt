package com.example.voice

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.data.PreferencesStore
import com.example.data.WalkieBackendClient
import com.example.data.WalkieFirestoreRepository
import com.example.data.normalizeBackendBaseUrl
import com.example.model.ChannelParticipant
import com.example.model.FloorState
import com.example.model.OnlineStatus
import com.example.model.PublicProfile
import com.example.model.VoiceConnectionState
import com.example.model.WalkieRoom
import com.example.model.WalkieSessionSnapshot
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.track.DataPublishReliability
import io.livekit.android.room.participant.Participant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WalkieSessionManager(
    private val appContext: Context,
    private val backendClient: WalkieBackendClient,
    private val firestoreRepository: WalkieFirestoreRepository,
    private val preferencesStore: PreferencesStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _sessionState = MutableStateFlow(WalkieSessionSnapshot())
    val sessionState: StateFlow<WalkieSessionSnapshot> = _sessionState.asStateFlow()

    private var livekitRoom: Room? = null
    private var eventCollectorJob: Job? = null
    private var heartbeatJob: Job? = null
    private var floorPollJob: Job? = null
    private var connectJob: Job? = null

    private var currentProfile: PublicProfile? = null
    private var currentBackendUrl: String = ""

    @Volatile
    private var isPttHeld: Boolean = false

    init {
        scope.launch {
            val speakerOn = preferencesStore.speakerphoneEnabledFlow.first()
            applySpeakerphoneSetting(speakerOn)
            _sessionState.update { it.copy(isSpeakerphoneEnabled = speakerOn) }
        }
    }

    fun connectToRoom(
        room: WalkieRoom,
        profile: PublicProfile,
        backendUrl: String
    ) {
        connectJob?.cancel()
        connectJob = scope.launch {
            disconnectInternal(clearRoom = false)

            currentProfile = profile
            currentBackendUrl = normalizeBackendBaseUrl(backendUrl)

            if (currentBackendUrl.isEmpty()) {
                _sessionState.update {
                    it.copy(
                        activeRoom = room,
                        connectionState = VoiceConnectionState.ERROR,
                        errorMessage = "Set your LiveKit Token & Floor Control server URL in Profile -> Server Settings to connect."
                    )
                }
                return@launch
            }

            _sessionState.update {
                it.copy(
                    activeRoom = room,
                    connectionState = VoiceConnectionState.FETCHING_TOKEN,
                    floorState = FloorState(),
                    participants = emptyList(),
                    errorMessage = null
                )
            }

            val tokenResult = withContext(Dispatchers.IO) {
                backendClient.fetchLiveKitToken(
                    baseUrl = currentBackendUrl,
                    roomId = room.id,
                    livekitRoomName = room.livekitRoomName,
                    participantName = profile.callsign.ifBlank { profile.displayName }
                )
            }

            val tokenResponse = tokenResult.getOrElse { error ->
                Log.e(TAG, "Failed to obtain LiveKit token", error)
                _sessionState.update {
                    it.copy(
                        connectionState = VoiceConnectionState.ERROR,
                        errorMessage = error.message ?: "Unable to fetch voice token from backend."
                    )
                }
                return@launch
            }

            _sessionState.update { it.copy(connectionState = VoiceConnectionState.CONNECTING) }

            try {
                val lkRoom = LiveKit.create(appContext)
                livekitRoom = lkRoom
                observeRoomEvents(lkRoom, room)

                lkRoom.connect(
                    url = tokenResponse.serverUrl,
                    token = tokenResponse.token
                )

                // Half-duplex PTT: keep mic muted until server grants floor
                lkRoom.localParticipant.setMicrophoneEnabled(false)
                applySpeakerphoneSetting(_sessionState.value.isSpeakerphoneEnabled)

                _sessionState.update {
                    it.copy(
                        connectionState = VoiceConnectionState.CONNECTED,
                        errorMessage = null
                    )
                }
                refreshParticipants()
                startForegroundService()
                startFloorPolling(room.id)
                firestoreRepository.updateOnlineStatus(
                    userId = profile.userId,
                    status = OnlineStatus.IN_CHANNEL,
                    activeRoomId = room.id
                )
            } catch (e: Exception) {
                Log.e(TAG, "LiveKit connection failed", e)
                _sessionState.update {
                    it.copy(
                        connectionState = VoiceConnectionState.ERROR,
                        errorMessage = e.localizedMessage ?: "LiveKit connection failed. Check network and server URL."
                    )
                }
                disconnectInternal(clearRoom = false)
            }
        }
    }

    fun reconnect() {
        val room = _sessionState.value.activeRoom ?: return
        val profile = currentProfile ?: return
        scope.launch {
            val latestUrl = preferencesStore.backendUrlFlow.first()
            connectToRoom(room, profile, latestUrl)
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        scope.launch {
            disconnectInternal(clearRoom = true)
            currentProfile?.let { profile ->
                firestoreRepository.updateOnlineStatus(
                    userId = profile.userId,
                    status = OnlineStatus.ONLINE,
                    activeRoomId = null
                )
            }
        }
    }

    fun requestAndStartTransmit() {
        val snapshot = _sessionState.value
        val room = snapshot.activeRoom ?: return
        val profile = currentProfile ?: return
        val lkRoom = livekitRoom
        if (snapshot.connectionState != VoiceConnectionState.CONNECTED || lkRoom == null) {
            _sessionState.update {
                it.copy(errorMessage = "Connect to the voice channel before transmitting.")
            }
            return
        }
        if (isPttHeld || snapshot.floorState.isRequesting || snapshot.floorState.isHeldByMe) {
            return
        }

        isPttHeld = true
        _sessionState.update {
            it.copy(
                floorState = it.floorState.copy(isRequesting = true, busyMessage = null),
                errorMessage = null
            )
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                backendClient.acquireFloor(
                    baseUrl = currentBackendUrl,
                    roomId = room.id,
                    livekitRoomName = room.livekitRoomName,
                    participantName = profile.callsign.ifBlank { profile.displayName }
                )
            }

            if (!isPttHeld) {
                // User released button before server responded -> release immediately if granted
                result.getOrNull()?.let { response ->
                    val token = response.leaseToken
                    if (response.granted && !token.isNullOrBlank()) {
                        withContext(Dispatchers.IO + NonCancellable) {
                            backendClient.releaseFloor(currentBackendUrl, room.id, token)
                        }
                    }
                }
                _sessionState.update {
                    it.copy(floorState = it.floorState.copy(isRequesting = false))
                }
                return@launch
            }

            result.onSuccess { response ->
                if (response.granted && !response.leaseToken.isNullOrBlank()) {
                    try {
                        lkRoom.localParticipant.setMicrophoneEnabled(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to enable microphone", e)
                        isPttHeld = false
                        withContext(Dispatchers.IO + NonCancellable) {
                            backendClient.releaseFloor(currentBackendUrl, room.id, response.leaseToken)
                        }
                        _sessionState.update {
                            it.copy(
                                floorState = it.floorState.copy(isRequesting = false),
                                errorMessage = "Unable to activate microphone: ${e.localizedMessage}"
                            )
                        }
                        return@onSuccess
                    }

                    val speakerLabel = profile.callsign.ifBlank { profile.displayName }
                    _sessionState.update {
                        it.copy(
                            floorState = FloorState(
                                isHeldByMe = true,
                                isRequesting = false,
                                speakerUid = profile.userId,
                                speakerName = speakerLabel,
                                leaseToken = response.leaseToken,
                                leaseExpiresAtMs = response.leaseExpiresAtMs,
                                busyMessage = null
                            )
                        )
                    }
                    broadcastFloorPacket(
                        lkRoom = lkRoom,
                        speakerUid = profile.userId,
                        speakerName = speakerLabel,
                        active = true
                    )
                    startFloorHeartbeat(room.id, response.leaseToken)
                    launch {
                        firestoreRepository.syncRoomSpeakerDisplay(room.id, profile.userId, speakerLabel)
                    }
                    refreshParticipants()
                } else {
                    isPttHeld = false
                    val holder = response.speakerName ?: "Another operator"
                    _sessionState.update {
                        it.copy(
                            floorState = FloorState(
                                isHeldByMe = false,
                                isRequesting = false,
                                speakerUid = response.speakerUid,
                                speakerName = response.speakerName,
                                leaseExpiresAtMs = response.leaseExpiresAtMs,
                                busyMessage = "$holder is currently speaking"
                            )
                        )
                    }
                }
            }.onFailure { error ->
                isPttHeld = false
                _sessionState.update {
                    it.copy(
                        floorState = it.floorState.copy(isRequesting = false),
                        errorMessage = error.message ?: "Floor control request failed."
                    )
                }
            }
        }
    }

    fun stopTransmit() {
        val wasHeld = isPttHeld
        isPttHeld = false
        heartbeatJob?.cancel()
        heartbeatJob = null

        val snapshot = _sessionState.value
        val room = snapshot.activeRoom
        val leaseToken = snapshot.floorState.leaseToken
        val heldByMe = snapshot.floorState.isHeldByMe
        val lkRoom = livekitRoom

        if (!wasHeld && !heldByMe) {
            return
        }

        scope.launch {
            withContext(NonCancellable) {
                try {
                    lkRoom?.localParticipant?.setMicrophoneEnabled(false)
                } catch (e: Exception) {
                    Log.w(TAG, "Error disabling microphone on release", e)
                }

                if (heldByMe && lkRoom != null) {
                    broadcastFloorPacket(
                        lkRoom = lkRoom,
                        speakerUid = null,
                        speakerName = null,
                        active = false
                    )
                }

                if (heldByMe && room != null && !leaseToken.isNullOrBlank() && currentBackendUrl.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        backendClient.releaseFloor(currentBackendUrl, room.id, leaseToken)
                    }
                }

                if (heldByMe && room != null) {
                    firestoreRepository.syncRoomSpeakerDisplay(room.id, null, null)
                }
            }

            _sessionState.update {
                it.copy(
                    localAudioLevel = 0f,
                    floorState = if (it.floorState.isHeldByMe) {
                        FloorState()
                    } else {
                        it.floorState.copy(isRequesting = false)
                    }
                )
            }
            refreshParticipants()
        }
    }

    fun toggleSpeakerphone() {
        val next = !_sessionState.value.isSpeakerphoneEnabled
        applySpeakerphoneSetting(next)
        _sessionState.update { it.copy(isSpeakerphoneEnabled = next) }
        scope.launch {
            preferencesStore.setSpeakerphoneEnabled(next)
        }
    }

    fun clearError() {
        _sessionState.update {
            it.copy(
                errorMessage = null,
                floorState = it.floorState.copy(busyMessage = null)
            )
        }
    }

    fun markBackgroundServiceState(running: Boolean) {
        _sessionState.update { it.copy(isBackgroundServiceRunning = running) }
    }

    private fun startFloorHeartbeat(roomId: String, leaseToken: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isPttHeld) {
                delay(10_000L)
                if (!isPttHeld) break
                val res = withContext(Dispatchers.IO) {
                    backendClient.heartbeatFloor(currentBackendUrl, roomId, leaseToken)
                }
                res.onSuccess { response ->
                    if (!response.granted) {
                        stopTransmit()
                    }
                }.onFailure {
                    Log.w(TAG, "Floor heartbeat warning: ${it.message}")
                }
            }
        }
    }

    private fun startFloorPolling(roomId: String) {
        floorPollJob?.cancel()
        floorPollJob = scope.launch {
            while (isActive) {
                delay(3_500L)
                if (currentBackendUrl.isEmpty()) continue
                val res = withContext(Dispatchers.IO) {
                    backendClient.getFloorStatus(currentBackendUrl, roomId)
                }
                res.onSuccess { status ->
                    val myUid = currentProfile?.userId
                    _sessionState.update { current ->
                        if (current.floorState.isHeldByMe && status.speakerUid == myUid) {
                            current
                        } else if (status.speakerUid != null && status.speakerUid != myUid) {
                            current.copy(
                                floorState = current.floorState.copy(
                                    isHeldByMe = false,
                                    speakerUid = status.speakerUid,
                                    speakerName = status.speakerName,
                                    leaseExpiresAtMs = status.leaseExpiresAtMs
                                )
                            )
                        } else if (status.speakerUid == null && !current.floorState.isHeldByMe) {
                            current.copy(
                                floorState = current.floorState.copy(
                                    speakerUid = null,
                                    speakerName = null,
                                    busyMessage = null
                                )
                            )
                        } else {
                            current
                        }
                    }
                }
            }
        }
    }

    private fun observeRoomEvents(lkRoom: Room, walkieRoom: WalkieRoom) {
        eventCollectorJob?.cancel()
        eventCollectorJob = scope.launch {
            lkRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected -> {
                        _sessionState.update {
                            it.copy(
                                connectionState = VoiceConnectionState.CONNECTED,
                                errorMessage = null
                            )
                        }
                        refreshParticipants()
                    }

                    is RoomEvent.Reconnecting -> {
                        if (isPttHeld) stopTransmit()
                        _sessionState.update {
                            it.copy(connectionState = VoiceConnectionState.RECONNECTING)
                        }
                    }

                    is RoomEvent.Reconnected -> {
                        _sessionState.update {
                            it.copy(
                                connectionState = VoiceConnectionState.CONNECTED,
                                errorMessage = null
                            )
                        }
                        refreshParticipants()
                    }

                    is RoomEvent.Disconnected -> {
                        if (isPttHeld) stopTransmit()
                        _sessionState.update {
                            it.copy(
                                connectionState = VoiceConnectionState.DISCONNECTED,
                                localAudioLevel = 0f,
                                remoteAudioLevel = 0f
                            )
                        }
                        refreshParticipants()
                    }

                    is RoomEvent.FailedToConnect -> {
                        _sessionState.update {
                            it.copy(
                                connectionState = VoiceConnectionState.ERROR,
                                errorMessage = event.error.localizedMessage ?: "Failed to connect to LiveKit."
                            )
                        }
                    }

                    is RoomEvent.ParticipantConnected,
                    is RoomEvent.ParticipantDisconnected,
                    is RoomEvent.TrackSubscribed,
                    is RoomEvent.TrackUnsubscribed,
                    is RoomEvent.TrackMuted,
                    is RoomEvent.TrackUnmuted,
                    is RoomEvent.ConnectionQualityChanged -> {
                        refreshParticipants()
                    }

                    is RoomEvent.ActiveSpeakersChanged -> {
                        val myUid = currentProfile?.userId
                        val speakers = event.speakers
                        val localLevel = speakers.firstOrNull { it.identity?.value == myUid }?.audioLevel ?: 0f
                        val remoteSpeaker = speakers.firstOrNull { it.identity?.value != myUid }
                        val remoteLevel = remoteSpeaker?.audioLevel ?: 0f

                        _sessionState.update { current ->
                            val updatedFloor = if (remoteSpeaker != null && !current.floorState.isHeldByMe) {
                                current.floorState.copy(
                                    speakerUid = remoteSpeaker.identity?.value ?: current.floorState.speakerUid,
                                    speakerName = remoteSpeaker.name?.ifBlank { remoteSpeaker.identity?.value }
                                        ?: current.floorState.speakerName
                                )
                            } else {
                                current.floorState
                            }
                            current.copy(
                                localAudioLevel = localLevel,
                                remoteAudioLevel = remoteLevel,
                                floorState = updatedFloor
                            )
                        }
                        refreshParticipants()
                    }

                    is RoomEvent.DataReceived -> {
                        if (event.topic == TOPIC_FLOOR_CONTROL) {
                            handleFloorDataPacket(event.data)
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    private fun handleFloorDataPacket(data: ByteArray) {
        try {
            val json = JSONObject(data.toString(Charsets.UTF_8))
            val active = json.optBoolean("active", false)
            val speakerUid = json.optString("speakerUid", "").ifBlank { null }
            val speakerName = json.optString("speakerName", "").ifBlank { null }
            val myUid = currentProfile?.userId

            _sessionState.update { current ->
                if (active && speakerUid != null && speakerUid != myUid) {
                    current.copy(
                        floorState = FloorState(
                            isHeldByMe = false,
                            speakerUid = speakerUid,
                            speakerName = speakerName
                        )
                    )
                } else if (!active && !current.floorState.isHeldByMe) {
                    current.copy(
                        remoteAudioLevel = 0f,
                        floorState = FloorState()
                    )
                } else {
                    current
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Malformed floor data packet", e)
        }
    }

    private suspend fun broadcastFloorPacket(
        lkRoom: Room,
        speakerUid: String?,
        speakerName: String?,
        active: Boolean
    ) {
        try {
            val payload = JSONObject().apply {
                put("active", active)
                if (speakerUid != null) put("speakerUid", speakerUid)
                if (speakerName != null) put("speakerName", speakerName)
            }.toString().toByteArray(Charsets.UTF_8)

            lkRoom.localParticipant.publishData(
                data = payload,
                reliability = DataPublishReliability.RELIABLE,
                topic = TOPIC_FLOOR_CONTROL
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unable to broadcast floor packet", e)
        }
    }

    private fun refreshParticipants() {
        val lkRoom = livekitRoom ?: return
        val list = buildList {
            val local = lkRoom.localParticipant
            add(
                ChannelParticipant(
                    uid = local.identity?.value ?: currentProfile?.userId.orEmpty(),
                    name = local.name?.ifBlank { currentProfile?.callsign } ?: currentProfile?.callsign ?: "You",
                    isLocal = true,
                    isSpeaking = local.isSpeaking || _sessionState.value.floorState.isHeldByMe,
                    isMicrophoneEnabled = local.isMicrophoneEnabled,
                    qualityLabel = qualityLabel(local.connectionQuality)
                )
            )
            lkRoom.remoteParticipants.values.forEach { remote ->
                add(
                    ChannelParticipant(
                        uid = remote.identity?.value ?: remote.sid.value,
                        name = remote.name?.ifBlank { remote.identity?.value } ?: "Operator",
                        isLocal = false,
                        isSpeaking = remote.isSpeaking ||
                            remote.identity?.value == _sessionState.value.floorState.speakerUid,
                        isMicrophoneEnabled = remote.isMicrophoneEnabled,
                        qualityLabel = qualityLabel(remote.connectionQuality)
                    )
                )
            }
        }
        _sessionState.update { it.copy(participants = list) }
    }

    private fun qualityLabel(quality: ConnectionQuality): String = when (quality) {
        ConnectionQuality.EXCELLENT -> "HD+"
        ConnectionQuality.GOOD -> "HD"
        ConnectionQuality.POOR -> "WEAK"
        ConnectionQuality.LOST -> "LOST"
        else -> "SYNC"
    }

    private suspend fun disconnectInternal(clearRoom: Boolean) {
        isPttHeld = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        floorPollJob?.cancel()
        floorPollJob = null
        eventCollectorJob?.cancel()
        eventCollectorJob = null

        val currentSnapshot = _sessionState.value
        val activeRoom = currentSnapshot.activeRoom
        val leaseToken = currentSnapshot.floorState.leaseToken

        withContext(NonCancellable) {
            if (currentSnapshot.floorState.isHeldByMe &&
                activeRoom != null &&
                !leaseToken.isNullOrBlank() &&
                currentBackendUrl.isNotEmpty()
            ) {
                withContext(Dispatchers.IO) {
                    backendClient.releaseFloor(currentBackendUrl, activeRoom.id, leaseToken)
                }
            }

            try {
                livekitRoom?.localParticipant?.setMicrophoneEnabled(false)
                livekitRoom?.disconnect()
                livekitRoom?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing LiveKit room", e)
            }
            livekitRoom = null
        }

        if (clearRoom) {
            WalkieForegroundService.stop(appContext)
            _sessionState.value = WalkieSessionSnapshot(
                isSpeakerphoneEnabled = _sessionState.value.isSpeakerphoneEnabled
            )
        }
    }

    private fun applySpeakerphoneSetting(enabled: Boolean) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        } catch (e: Exception) {
            Log.w(TAG, "Unable to set speakerphone mode", e)
        }
    }

    private fun startForegroundService() {
        WalkieForegroundService.start(appContext)
    }

    private companion object {
        const val TAG = "WalkieSessionManager"
        const val TOPIC_FLOOR_CONTROL = "walkie_floor"
    }
}
