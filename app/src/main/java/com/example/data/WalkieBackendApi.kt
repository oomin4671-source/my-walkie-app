package com.example.data

import com.google.firebase.auth.FirebaseAuth
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.tasks.await
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class TokenRequest(
    val roomId: String,
    val livekitRoomName: String,
    val participantName: String
)

@JsonClass(generateAdapter = true)
data class TokenResponse(
    val token: String,
    val serverUrl: String,
    val identity: String
)

@JsonClass(generateAdapter = true)
data class FloorAcquireRequest(
    val roomId: String,
    val livekitRoomName: String,
    val participantName: String
)

@JsonClass(generateAdapter = true)
data class FloorLeaseRequest(
    val roomId: String,
    val leaseToken: String
)

@JsonClass(generateAdapter = true)
data class FloorControlResponse(
    val granted: Boolean = false,
    val speakerUid: String? = null,
    val speakerName: String? = null,
    val leaseToken: String? = null,
    val leaseExpiresAtMs: Long = 0L,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class BackendHealthResponse(
    val ok: Boolean = false,
    val service: String? = null,
    val status: String? = null,
    val livekitConfigured: Boolean = false,
    val firebaseProjectConfigured: Boolean = false
)

@JsonClass(generateAdapter = true)
data class BackendErrorBody(
    val error: String? = null,
    val message: String? = null
)

private val KNOWN_ENDPOINT_SUFFIXES = listOf(
    "/api/livekit/token",
    "/livekit/token",
    "/api/floor/acquire",
    "/floor/acquire",
    "/api/floor/heartbeat",
    "/floor/heartbeat",
    "/api/floor/release",
    "/floor/release",
    "/api/floor/status",
    "/floor/status",
    "/api/health",
    "/healthz",
    "/health",
    "/api"
)

/**
 * Normalizes a user-supplied backend URL into a clean scheme + host (+ optional proxy path)
 * without trailing `/health`, `/healthz`, `/api`, or endpoint paths that would otherwise
 * produce 404 URLs like `/healthz/api/livekit/token` or `/api/api/livekit/token`.
 */
fun normalizeBackendBaseUrl(rawUrl: String): String {
    var cleaned = rawUrl.trim()
    if (cleaned.isEmpty()) return ""

    if (!cleaned.startsWith("http://", ignoreCase = true) &&
        !cleaned.startsWith("https://", ignoreCase = true)
    ) {
        cleaned = "https://$cleaned"
    }

    cleaned = cleaned.substringBefore('?').substringBefore('#').trimEnd('/')

    var stripped: Boolean
    do {
        stripped = false
        for (suffix in KNOWN_ENDPOINT_SUFFIXES) {
            if (cleaned.endsWith(suffix, ignoreCase = true)) {
                cleaned = cleaned.dropLast(suffix.length).trimEnd('/')
                stripped = true
                break
            }
        }
    } while (stripped)

    return cleaned
}

interface WalkieBackendService {
    @GET("health")
    suspend fun checkHealth(): BackendHealthResponse

    @POST("api/livekit/token")
    suspend fun fetchLiveKitToken(
        @Header("Authorization") bearerToken: String,
        @Body request: TokenRequest
    ): TokenResponse

    @POST("api/floor/acquire")
    suspend fun acquireFloor(
        @Header("Authorization") bearerToken: String,
        @Body request: FloorAcquireRequest
    ): FloorControlResponse

    @POST("api/floor/heartbeat")
    suspend fun heartbeatFloor(
        @Header("Authorization") bearerToken: String,
        @Body request: FloorLeaseRequest
    ): FloorControlResponse

    @POST("api/floor/release")
    suspend fun releaseFloor(
        @Header("Authorization") bearerToken: String,
        @Body request: FloorLeaseRequest
    ): FloorControlResponse

    @GET("api/floor/status")
    suspend fun getFloorStatus(
        @Header("Authorization") bearerToken: String,
        @Query("roomId") roomId: String
    ): FloorControlResponse
}

class WalkieBackendClient(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedService: WalkieBackendService? = null

    private fun getService(baseUrl: String): WalkieBackendService {
        val normalized = normalizeBackendBaseUrl(baseUrl)
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) {
            "Backend server URL must start with https:// or http:// (configure in Profile -> Server)."
        }
        val finalBaseUrl = "$normalized/"
        require(finalBaseUrl.toHttpUrlOrNull() != null) {
            "Invalid backend URL '$normalized'. Enter your root Render URL, e.g. https://my-walkie-backend.onrender.com"
        }
        if (cachedBaseUrl == finalBaseUrl && cachedService != null) {
            return cachedService!!
        }
        val service = Retrofit.Builder()
            .baseUrl(finalBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WalkieBackendService::class.java)
        cachedBaseUrl = finalBaseUrl
        cachedService = service
        return service
    }

    private suspend fun requireAuthHeader(): String {
        val user = auth.currentUser
            ?: throw IllegalStateException("Please sign in with Google before connecting to voice.")
        val tokenResult = user.getIdToken(false).await()
        val idToken = tokenResult.token
            ?: throw IllegalStateException("Failed to obtain Firebase ID token.")
        return "Bearer $idToken"
    }

    suspend fun checkHealth(baseUrl: String): Result<BackendHealthResponse> = runBackendCall {
        getService(baseUrl).checkHealth()
    }

    suspend fun fetchLiveKitToken(
        baseUrl: String,
        roomId: String,
        livekitRoomName: String,
        participantName: String
    ): Result<TokenResponse> = runBackendCall {
        val service = getService(baseUrl)
        val header = requireAuthHeader()
        service.fetchLiveKitToken(
            bearerToken = header,
            request = TokenRequest(
                roomId = roomId,
                livekitRoomName = livekitRoomName,
                participantName = participantName
            )
        )
    }

    suspend fun acquireFloor(
        baseUrl: String,
        roomId: String,
        livekitRoomName: String,
        participantName: String
    ): Result<FloorControlResponse> = runBackendCall {
        val service = getService(baseUrl)
        val header = requireAuthHeader()
        service.acquireFloor(
            bearerToken = header,
            request = FloorAcquireRequest(
                roomId = roomId,
                livekitRoomName = livekitRoomName,
                participantName = participantName
            )
        )
    }

    suspend fun heartbeatFloor(
        baseUrl: String,
        roomId: String,
        leaseToken: String
    ): Result<FloorControlResponse> = runBackendCall {
        val service = getService(baseUrl)
        val header = requireAuthHeader()
        service.heartbeatFloor(
            bearerToken = header,
            request = FloorLeaseRequest(roomId = roomId, leaseToken = leaseToken)
        )
    }

    suspend fun releaseFloor(
        baseUrl: String,
        roomId: String,
        leaseToken: String
    ): Result<FloorControlResponse> = runBackendCall {
        val service = getService(baseUrl)
        val header = requireAuthHeader()
        service.releaseFloor(
            bearerToken = header,
            request = FloorLeaseRequest(roomId = roomId, leaseToken = leaseToken)
        )
    }

    suspend fun getFloorStatus(
        baseUrl: String,
        roomId: String
    ): Result<FloorControlResponse> = runBackendCall {
        val service = getService(baseUrl)
        val header = requireAuthHeader()
        service.getFloorStatus(bearerToken = header, roomId = roomId)
    }

    private suspend inline fun <T> runBackendCall(crossinline block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: HttpException) {
            val rawError = try {
                e.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            val parsed = rawError?.let {
                try {
                    moshi.adapter(BackendErrorBody::class.java).fromJson(it)
                } catch (_: Exception) {
                    null
                }
            }
            val request = e.response()?.raw()?.request
            val requestTarget = if (request != null) {
                "${request.method} ${request.url}"
            } else {
                "backend endpoint"
            }
            val backendMessage = parsed?.error ?: parsed?.message
            val msg = when {
                e.code() == 404 && !backendMessage.isNullOrBlank() ->
                    "HTTP 404 on $requestTarget — $backendMessage"
                e.code() == 404 ->
                    "HTTP 404 on $requestTarget — Endpoint not found on server. Verify your Render Root Directory is 'backend' and latest code is deployed."
                !backendMessage.isNullOrBlank() ->
                    "$backendMessage (HTTP ${e.code()})"
                else ->
                    "Server returned HTTP ${e.code()} for $requestTarget"
            }
            Result.failure(IllegalStateException(msg, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
