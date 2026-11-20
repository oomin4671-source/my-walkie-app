package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BackendHealthResponse
import com.example.data.NetworkMonitor
import com.example.data.PreferencesStore
import com.example.data.WalkieBackendClient
import com.example.data.WalkieFirestoreRepository
import com.example.data.normalizeBackendBaseUrl
import com.example.model.NetworkTransport
import com.example.model.OnlineStatus
import com.example.model.PublicProfile
import com.example.model.UiState
import com.example.model.UserPrivate
import com.example.model.WalkieRoom
import com.example.model.WalkieSessionSnapshot
import com.example.voice.WalkieSessionManager
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

data class UiFeedback(
    val message: String,
    val isError: Boolean = false
)

sealed interface BackendTestState {
    data object Idle : BackendTestState
    data object Testing : BackendTestState
    data class Success(
        val health: BackendHealthResponse,
        val normalizedUrl: String
    ) : BackendTestState
    data class Error(
        val message: String,
        val normalizedUrl: String
    ) : BackendTestState
}

class WalkieMainViewModel(
    private val repository: WalkieFirestoreRepository,
    private val sessionManager: WalkieSessionManager,
    private val backendClient: WalkieBackendClient,
    private val preferencesStore: PreferencesStore,
    networkMonitor: NetworkMonitor,
    private val currentUserId: String
) : ViewModel() {

    private val _feedback = MutableSharedFlow<UiFeedback>(extraBufferCapacity = 8)
    val feedback: SharedFlow<UiFeedback> = _feedback.asSharedFlow()

    private val _backendTestState = MutableStateFlow<BackendTestState>(BackendTestState.Idle)
    val backendTestState: StateFlow<BackendTestState> = _backendTestState.asStateFlow()

    val myProfileState: StateFlow<UiState<PublicProfile?>> =
        repository.observeMyProfile(currentUserId)
            .map<PublicProfile?, UiState<PublicProfile?>> { UiState.Success(it) }
            .catch { error -> emitStreamError(error, "profile") }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val myPrivateSettings: StateFlow<UserPrivate?> =
        repository.observeMyPrivateSettings(currentUserId)
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing private settings", firestoreError ?: error)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null
            )

    val roomsState: StateFlow<UiState<List<WalkieRoom>>> =
        repository.observeMyRooms(currentUserId)
            .map<List<WalkieRoom>, UiState<List<WalkieRoom>>> { UiState.Success(it) }
            .catch { error -> emitStreamError(error, "rooms") }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val contactsState: StateFlow<UiState<List<PublicProfile>>> =
        repository.observeDiscoverableProfiles()
            .map<List<PublicProfile>, UiState<List<PublicProfile>>> { list ->
                UiState.Success(list.filter { it.userId != currentUserId })
            }
            .catch { error -> emitStreamError(error, "contacts") }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val sessionState: StateFlow<WalkieSessionSnapshot> = sessionManager.sessionState

    val networkTransport: StateFlow<NetworkTransport> =
        networkMonitor.observeTransport()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = NetworkTransport.UNKNOWN
            )

    val backendUrl: StateFlow<String> =
        preferencesStore.backendUrlFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = ""
            )

    val hapticsEnabled: StateFlow<Boolean> =
        preferencesStore.hapticsEnabledFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = true
            )

    fun ensureUserInitialized(user: FirebaseUser) {
        viewModelScope.launch {
            repository.ensureUserInitialized(user).onFailure { error ->
                _feedback.tryEmit(
                    UiFeedback(error.localizedMessage ?: "Failed to initialize profile", isError = true)
                )
            }
        }
    }

    fun connectToRoom(room: WalkieRoom) {
        val profile = (myProfileState.value as? UiState.Success)?.data
        if (profile == null) {
            _feedback.tryEmit(UiFeedback("Profile still loading, try again in a moment.", isError = true))
            return
        }
        val effectiveUrl = normalizeBackendBaseUrl(
            backendUrl.value.ifBlank {
                myPrivateSettings.value?.backendUrl.orEmpty()
            }
        )
        sessionManager.connectToRoom(room, profile, effectiveUrl)
    }

    fun testBackendConnection(rawUrl: String) {
        val normalized = normalizeBackendBaseUrl(rawUrl)
        if (normalized.isBlank()) {
            _backendTestState.value = BackendTestState.Error(
                message = "Enter your Render backend URL first (e.g. https://my-walkie-backend.onrender.com).",
                normalizedUrl = ""
            )
            return
        }
        viewModelScope.launch {
            _backendTestState.value = BackendTestState.Testing
            val result = withContext(Dispatchers.IO) {
                backendClient.checkHealth(normalized)
            }
            result.onSuccess { health ->
                if (!health.ok) {
                    _backendTestState.value = BackendTestState.Error(
                        message = "Endpoint responded, but health check returned ok=false. Check Render deploy logs.",
                        normalizedUrl = normalized
                    )
                } else {
                    _backendTestState.value = BackendTestState.Success(
                        health = health,
                        normalizedUrl = normalized
                    )
                }
            }.onFailure { error ->
                _backendTestState.value = BackendTestState.Error(
                    message = error.message ?: "Unable to reach $normalized/health",
                    normalizedUrl = normalized
                )
            }
        }
    }

    fun clearBackendTestState() {
        _backendTestState.value = BackendTestState.Idle
    }

    fun disconnectChannel() {
        sessionManager.disconnect()
    }

    fun reconnectChannel() {
        sessionManager.reconnect()
    }

    fun onPttPressStart() {
        sessionManager.requestAndStartTransmit()
    }

    fun onPttPressEnd() {
        sessionManager.stopTransmit()
    }

    fun toggleSpeakerphone() {
        sessionManager.toggleSpeakerphone()
    }

    fun clearSessionError() {
        sessionManager.clearError()
    }

    fun createGroupRoom(
        name: String,
        description: String,
        maxParticipants: Int,
        autoConnect: Boolean = true,
        onCreated: (WalkieRoom) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createGroupRoom(name, description, maxParticipants)
                .onSuccess { room ->
                    _feedback.tryEmit(UiFeedback("Channel '${room.name}' created • Code ${room.joinCode}"))
                    if (autoConnect) {
                        connectToRoom(room)
                    }
                    onCreated(room)
                }
                .onFailure { error ->
                    _feedback.tryEmit(
                        UiFeedback(error.localizedMessage ?: "Failed to create channel", isError = true)
                    )
                }
        }
    }

    fun joinGroupByCode(
        code: String,
        autoConnect: Boolean = true,
        onJoined: (WalkieRoom) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.joinGroupRoomByCode(code)
                .onSuccess { room ->
                    _feedback.tryEmit(UiFeedback("Joined '${room.name}'"))
                    if (autoConnect) {
                        connectToRoom(room)
                    }
                    onJoined(room)
                }
                .onFailure { error ->
                    _feedback.tryEmit(
                        UiFeedback(error.localizedMessage ?: "Could not join channel", isError = true)
                    )
                }
        }
    }

    fun startPrivateTalk(
        target: PublicProfile,
        onOpened: (WalkieRoom) -> Unit = {}
    ) {
        val myProfile = (myProfileState.value as? UiState.Success)?.data
        if (myProfile == null) {
            _feedback.tryEmit(UiFeedback("Operator profile not ready yet.", isError = true))
            return
        }
        viewModelScope.launch {
            repository.openOrCreatePrivateRoom(myProfile, target)
                .onSuccess { room ->
                    connectToRoom(room)
                    onOpened(room)
                }
                .onFailure { error ->
                    _feedback.tryEmit(
                        UiFeedback(error.localizedMessage ?: "Failed to open Private Talk", isError = true)
                    )
                }
        }
    }

    fun leaveGroupRoom(room: WalkieRoom) {
        viewModelScope.launch {
            if (sessionState.value.activeRoom?.id == room.id) {
                sessionManager.disconnect()
            }
            repository.leaveGroupRoom(room)
                .onSuccess { _feedback.tryEmit(UiFeedback("Left '${room.name}'")) }
                .onFailure { error ->
                    _feedback.tryEmit(
                        UiFeedback(error.localizedMessage ?: "Unable to leave channel", isError = true)
                    )
                }
        }
    }

    fun deleteRoom(room: WalkieRoom) {
        viewModelScope.launch {
            if (sessionState.value.activeRoom?.id == room.id) {
                sessionManager.disconnect()
            }
            repository.deleteRoom(room)
                .onSuccess { _feedback.tryEmit(UiFeedback("Deleted '${room.name}'")) }
                .onFailure { error ->
                    _feedback.tryEmit(
                        UiFeedback(error.localizedMessage ?: "Unable to delete channel", isError = true)
                    )
                }
        }
    }

    fun updateProfile(
        displayName: String,
        callsign: String,
        statusMessage: String,
        discoverable: Boolean
    ) {
        viewModelScope.launch {
            repository.updateProfile(
                userId = currentUserId,
                displayName = displayName,
                callsign = callsign,
                statusMessage = statusMessage,
                discoverable = discoverable
            ).onSuccess {
                _feedback.tryEmit(UiFeedback("Operator profile updated"))
            }.onFailure { error ->
                _feedback.tryEmit(
                    UiFeedback(error.localizedMessage ?: "Profile update failed", isError = true)
                )
            }
        }
    }

    fun updateOnlineStatus(status: OnlineStatus) {
        viewModelScope.launch {
            val activeRoomId = sessionState.value.activeRoom?.id
            repository.updateOnlineStatus(currentUserId, status, activeRoomId)
                .onFailure { error ->
                    _feedback.tryEmit(
                        UiFeedback(error.localizedMessage ?: "Status update failed", isError = true)
                    )
                }
        }
    }

    fun saveSettings(
        backendServerUrl: String,
        haptics: Boolean,
        autoJoinAudio: Boolean
    ) {
        viewModelScope.launch {
            val normalizedUrl = normalizeBackendBaseUrl(backendServerUrl)
            preferencesStore.setBackendUrl(normalizedUrl)
            preferencesStore.setHapticsEnabled(haptics)
            repository.updatePrivatePreferences(
                userId = currentUserId,
                hapticsEnabled = haptics,
                autoJoinAudio = autoJoinAudio,
                backendUrl = normalizedUrl
            ).onSuccess {
                _feedback.tryEmit(UiFeedback("Settings saved ($normalizedUrl)"))
            }.onFailure { error ->
                _feedback.tryEmit(
                    UiFeedback(error.localizedMessage ?: "Failed to sync settings", isError = true)
                )
            }
        }
    }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<UiState<T>>.emitStreamError(
        error: Throwable,
        streamName: String
    ) {
        val firestoreError = unwrapFirestoreError(error)
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $streamName", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to observe $streamName"))
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? {
        return generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
    }

    private companion object {
        const val TAG = "WalkieMainVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
