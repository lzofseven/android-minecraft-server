package com.lzofseven.mcserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lzofseven.mcserver.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ServerService : Service() {
    
    private val CHANNEL_ID = "MinecraftServerChannel"
    private val NOTIFICATION_ID = 1
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // TODO: Start server process
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servidor Minecraft",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação do servidor Minecraft"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Servidor Minecraft")
            .setContentText("Servidor está rodando")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
