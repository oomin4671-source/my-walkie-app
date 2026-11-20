package com.example.data

import android.content.Context
import com.example.R
import com.example.model.OnlineStatus
import com.example.model.PublicProfile
import com.example.model.RoomInvite
import com.example.model.RoomType
import com.example.model.UserPrivate
import com.example.model.WalkieRoom
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import kotlin.coroutines.cancellation.CancellationException

class WalkieFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun ensureUserInitialized(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val privateRef = db.collection("users").document(uid)
        val publicRef = db.collection("public_profiles").document(uid)
        return try {
            val existingPrivate = privateRef.get().await()
            if (!existingPrivate.exists()) {
                val privatePayload = mapOf(
                    "userId" to uid,
                    "email" to user.email?.take(200),
                    "hapticsEnabled" to true,
                    "autoJoinAudio" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                privateRef.set(privatePayload).await()
            }

            val existingPublic = publicRef.get().await()
            if (!existingPublic.exists()) {
                val rawName = (user.displayName ?: "Operator").trim().take(50).ifBlank { "Operator" }
                val defaultCallsign = generateDefaultCallsign(rawName, uid)
                val publicPayload = mapOf(
                    "userId" to uid,
                    "displayName" to rawName,
                    "callsign" to defaultCallsign,
                    "statusMessage" to "Ready on Push-to-Talk",
                    "avatarColorHex" to pickAvatarAccent(uid),
                    "photoUrl" to user.photoUrl?.toString()?.take(500),
                    "onlineStatus" to OnlineStatus.ONLINE.name,
                    "discoverable" to true,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                publicRef.set(publicPayload).await()
            } else {
                publicRef.update(
                    mapOf(
                        "onlineStatus" to OnlineStatus.ONLINE.name,
                        "lastSeenAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    fun observeMyPrivateSettings(userId: String): Flow<UserPrivate?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user private settings read." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        UserPrivate::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.GET, path, uid) }
        )
    }

    fun observeMyProfile(userId: String): Flow<PublicProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user own profile stream." }
        val path = "public_profiles/$uid"
        emitAll(
            db.collection("public_profiles").document(uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        PublicProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.GET, path, uid) }
        )
    }

    fun observeDiscoverableProfiles(): Flow<List<PublicProfile>> = flow {
        val uid = requireUserId()
        val path = "public_profiles"
        emitAll(
            db.collection("public_profiles")
                .whereEqualTo("discoverable", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PublicProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(
                        compareBy<PublicProfile> { it.parsedStatus == OnlineStatus.OFFLINE }
                            .thenBy { it.displayName.lowercase() }
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeMyRooms(userId: String): Flow<List<WalkieRoom>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user rooms stream." }
        val path = "rooms"
        emitAll(
            db.collection("rooms")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WalkieRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error -> handleStreamError(error, OperationType.LIST, path, uid) }
        )
    }

    fun observeRoom(roomId: String): Flow<WalkieRoom?> = flow {
        val uid = requireUserId()
        val path = "rooms/$roomId"
        emitAll(
            db.collection("rooms").document(roomId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(
                        WalkieRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error -> handleStreamError(error, OperationType.GET, path, uid) }
        )
    }

    suspend fun updateOnlineStatus(
        userId: String,
        status: OnlineStatus,
        activeRoomId: String? = null
    ): Result<Unit> {
        val path = "public_profiles/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val payload = mutableMapOf<String, Any>(
                "onlineStatus" to status.name,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (activeRoomId != null && activeRoomId.isNotBlank()) {
                payload["activeRoomId"] = activeRoomId.take(128)
            } else {
                payload["activeRoomId"] = FieldValue.delete()
            }
            db.collection("public_profiles").document(uid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        userId: String,
        displayName: String,
        callsign: String,
        statusMessage: String,
        discoverable: Boolean
    ): Result<Unit> {
        val path = "public_profiles/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanName = displayName.trim().take(50)
            val cleanCallsign = callsign.trim().uppercase().take(24)
            val cleanStatus = statusMessage.trim().take(120)
            require(cleanName.isNotEmpty()) { "Display name cannot be empty." }
            require(cleanCallsign.length >= 2) { "Callsign must be at least 2 characters." }

            db.collection("public_profiles").document(uid).update(
                mapOf(
                    "displayName" to cleanName,
                    "callsign" to cleanCallsign,
                    "statusMessage" to cleanStatus,
                    "discoverable" to discoverable,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePrivatePreferences(
        userId: String,
        hapticsEnabled: Boolean,
        autoJoinAudio: Boolean,
        backendUrl: String
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch." }
            val cleanUrl = normalizeBackendBaseUrl(backendUrl).take(500)
            val payload = mutableMapOf<String, Any>(
                "hapticsEnabled" to hapticsEnabled,
                "autoJoinAudio" to autoJoinAudio,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanUrl.isNotEmpty()) {
                payload["backendUrl"] = cleanUrl
            } else {
                payload["backendUrl"] = FieldValue.delete()
            }
            db.collection("users").document(uid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun createGroupRoom(
        name: String,
        description: String,
        maxParticipants: Int = 20
    ): Result<WalkieRoom> {
        val cleanName = name.trim().take(60)
        val cleanDesc = description.trim().take(160)
        return try {
            val uid = requireUserId()
            require(cleanName.isNotEmpty()) { "Channel name is required." }
            val clampedMax = maxParticipants.coerceIn(2, 50)
            val roomRef = db.collection("rooms").document()
            val roomId = roomRef.id
            val joinCode = generateJoinCode()
            val livekitName = "walkie_grp_$roomId"

            val roomPayload = mutableMapOf<String, Any>(
                "name" to cleanName,
                "roomType" to RoomType.GROUP.name,
                "joinCode" to joinCode,
                "livekitRoomName" to livekitName,
                "ownerId" to uid,
                "memberUids" to listOf(uid),
                "maxParticipants" to clampedMax,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (cleanDesc.isNotEmpty()) {
                roomPayload["description"] = cleanDesc
            }

            val inviteRef = db.collection("room_invites").document(joinCode)
            val invitePayload = mapOf(
                "roomId" to roomId,
                "roomName" to cleanName,
                "ownerId" to uid,
                "createdAt" to FieldValue.serverTimestamp()
            )

            db.runBatch { batch ->
                batch.set(roomRef, roomPayload)
                batch.set(inviteRef, invitePayload)
            }.await()

            val created = roomRef.get().await().toObject(
                WalkieRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: WalkieRoom(
                id = roomId,
                name = cleanName,
                description = cleanDesc.ifEmpty { null },
                roomType = RoomType.GROUP.name,
                joinCode = joinCode,
                livekitRoomName = livekitName,
                ownerId = uid,
                memberUids = listOf(uid),
                maxParticipants = clampedMax.toLong()
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "rooms")
            Result.failure(e)
        }
    }

    suspend fun joinGroupRoomByCode(rawCode: String): Result<WalkieRoom> {
        val code = rawCode.trim().uppercase()
        return try {
            val uid = requireUserId()
            require(code.matches(Regex("^[A-Z0-9]{4,12}$"))) {
                "Enter a valid alphanumeric channel code (e.g. WK4829)."
            }
            val inviteSnap = db.collection("room_invites").document(code).get().await()
            if (!inviteSnap.exists()) {
                return Result.failure(IllegalArgumentException("No channel found for code $code."))
            }
            val invite = inviteSnap.toObject(RoomInvite::class.java)
                ?: return Result.failure(IllegalStateException("Invalid invite record."))
            val roomRef = db.collection("rooms").document(invite.roomId)

            val roomSnap = roomRef.get().await()
            if (roomSnap.exists()) {
                val existing = roomSnap.toObject(
                    WalkieRoom::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null && uid in existing.memberUids) {
                    return Result.success(existing)
                }
            }

            roomRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val joined = roomRef.get().await().toObject(
                WalkieRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Joined room could not be loaded."))
            Result.success(joined)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "room_invites/$code")
            Result.failure(e)
        }
    }

    suspend fun openOrCreatePrivateRoom(
        myProfile: PublicProfile,
        targetProfile: PublicProfile
    ): Result<WalkieRoom> {
        return try {
            val uid = requireUserId()
            require(targetProfile.userId.isNotBlank() && targetProfile.userId != uid) {
                "Select another operator for Private Talk."
            }
            val sortedUids = listOf(uid, targetProfile.userId).sorted()
            val deterministicId = "dm_${sortedUids[0]}_${sortedUids[1]}"
            val roomRef = db.collection("rooms").document(deterministicId)
            val existingSnap = try {
                roomRef.get().await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) null else throw e
            }

            if (existingSnap != null && existingSnap.exists()) {
                val room = existingSnap.toObject(
                    WalkieRoom::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (room != null) return Result.success(room)
            }

            val roomName = "${myProfile.callsign} ↔ ${targetProfile.callsign}".take(60)
            val payload = mapOf(
                "name" to roomName,
                "description" to "1-on-1 Private Talk channel",
                "roomType" to RoomType.PRIVATE.name,
                "joinCode" to "DM${sortedUids[0].take(3).uppercase()}${sortedUids[1].take(3).uppercase()}",
                "livekitRoomName" to deterministicId,
                "ownerId" to uid,
                "memberUids" to sortedUids,
                "maxParticipants" to 2,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            roomRef.set(payload).await()
            val created = roomRef.get().await().toObject(
                WalkieRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: WalkieRoom(
                id = deterministicId,
                name = roomName,
                description = "1-on-1 Private Talk channel",
                roomType = RoomType.PRIVATE.name,
                joinCode = "DM",
                livekitRoomName = deterministicId,
                ownerId = uid,
                memberUids = sortedUids,
                maxParticipants = 2L
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "rooms/private")
            Result.failure(e)
        }
    }

    suspend fun leaveGroupRoom(room: WalkieRoom): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.parsedType == RoomType.GROUP) { "Cannot leave a private 1-on-1 channel." }
            require(room.ownerId != uid) { "Channel owner can delete the channel instead of leaving." }
            db.collection("rooms").document(room.id).update(
                mapOf(
                    "memberUids" to FieldValue.arrayRemove(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteRoom(room: WalkieRoom): Result<Unit> {
        val path = "rooms/${room.id}"
        return try {
            val uid = requireUserId()
            require(room.ownerId == uid) { "Only the channel owner can delete this channel." }
            db.runBatch { batch ->
                batch.delete(db.collection("rooms").document(room.id))
                if (room.parsedType == RoomType.GROUP && room.joinCode.isNotBlank()) {
                    batch.delete(db.collection("room_invites").document(room.joinCode))
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun syncRoomSpeakerDisplay(
        roomId: String,
        speakerUid: String?,
        speakerName: String?
    ): Result<Unit> {
        val path = "rooms/$roomId"
        return try {
            requireUserId()
            val payload = mutableMapOf<String, Any>(
                "floorUpdatedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!speakerUid.isNullOrBlank() && !speakerName.isNullOrBlank()) {
                payload["activeSpeakerId"] = speakerUid.take(128)
                payload["activeSpeakerName"] = speakerName.take(60)
            } else {
                payload["activeSpeakerId"] = FieldValue.delete()
                payload["activeSpeakerName"] = FieldValue.delete()
            }
            db.collection("rooms").document(roomId).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private fun handleStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return buildString(6) {
            repeat(6) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }

    private fun generateDefaultCallsign(displayName: String, uid: String): String {
        val prefix = displayName
            .uppercase()
            .replace(Regex("[^A-Z0-9]"), "")
            .take(5)
            .ifEmpty { "OP" }
        val suffix = uid.takeLast(3).uppercase()
        return "$prefix-$suffix"
    }

    private fun pickAvatarAccent(uid: String): String {
        val palette = listOf("#FF6B2C", "#00E5FF", "#10B981", "#F59E0B", "#38BDF8")
        val idx = (uid.hashCode() and Int.MAX_VALUE) % palette.size
        return palette[idx]
    }
}
