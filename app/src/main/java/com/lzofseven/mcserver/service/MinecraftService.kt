package com.lzofseven.mcserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lzofseven.mcserver.R
import com.lzofseven.mcserver.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MinecraftService : Service() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun createNotification(): Notification {
        // Ensure channel exists
        notificationHelper.createChannels()

        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_STATUS)
            .setContentTitle("Servidor Minecraft Rodando")
            .setContentText("O servidor está ativo em background.")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Placeholder icon, replace with R.drawable.ic_stat_server if available
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1337
        const val ACTION_START = "com.lzofseven.mcserver.service.START"
        const val ACTION_STOP = "com.lzofseven.mcserver.service.STOP"

        fun start(context: Context) {
            val intent = Intent(context, MinecraftService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MinecraftService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
