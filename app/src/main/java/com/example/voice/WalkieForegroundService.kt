package com.example.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.MyWalkieApp
import com.example.R
import com.example.model.VoiceConnectionState
import com.example.model.WalkieSessionSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WalkieForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectorJob: Job? = null

    private val sessionManager: WalkieSessionManager
        get() = (application as MyWalkieApp).sessionManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                sessionManager.disconnect()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                val initialNotification = buildNotification(sessionManager.sessionState.value)
                startForegroundCompat(initialNotification)
                sessionManager.markBackgroundServiceState(true)
                observeSessionUpdates()
            }
        }
        return START_STICKY
    }

    private fun observeSessionUpdates() {
        collectorJob?.cancel()
        collectorJob = serviceScope.launch {
            sessionManager.sessionState.collectLatest { state ->
                if (state.activeRoom == null && state.connectionState == VoiceConnectionState.DISCONNECTED) {
                    stopForegroundCompat()
                    stopSelf()
                } else {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, buildNotification(state))
                }
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        sessionManager.markBackgroundServiceState(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun buildNotification(state: WalkieSessionSnapshot): Notification {
        val roomName = state.activeRoom?.name ?: getString(R.string.channel_notification_title)
        val statusText = when {
            state.floorState.isHeldByMe -> "Transmitting live audio…"
            !state.floorState.speakerName.isNullOrBlank() ->
                "Receiving: ${state.floorState.speakerName}"
            state.connectionState == VoiceConnectionState.RECONNECTING -> "Reconnecting channel…"
            state.connectionState == VoiceConnectionState.CONNECTED ->
                "Listening in background • ${state.participants.size} active"
            state.connectionState == VoiceConnectionState.ERROR ->
                state.errorMessage ?: "Connection interrupted"
            else -> "Standby"
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WalkieForegroundService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("MY WALKIE • $roomName")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.disconnect),
                disconnectIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Walkie-Talkie Background Listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps live push-to-talk audio reception active while minimized"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        sessionManager.markBackgroundServiceState(false)
        collectorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "walkie_voice_channel"
        private const val NOTIFICATION_ID = 4091
        private const val ACTION_START = "com.example.voice.ACTION_START"
        private const val ACTION_DISCONNECT = "com.example.voice.ACTION_DISCONNECT"

        fun start(context: Context) {
            val intent = Intent(context, WalkieForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WalkieForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
