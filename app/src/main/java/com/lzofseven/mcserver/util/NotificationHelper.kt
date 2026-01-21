package com.lzofseven.mcserver.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lzofseven.mcserver.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun getAppIconInfo(): android.graphics.Bitmap {
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        return if (drawable is android.graphics.drawable.BitmapDrawable) {
            drawable.bitmap
        } else {
             // Handle Adaptive Icon
             val bitmap = android.graphics.Bitmap.createBitmap(
                 drawable!!.intrinsicWidth.takeIf { it > 0 } ?: 108, 
                 drawable.intrinsicHeight.takeIf { it > 0 } ?: 108, 
                 android.graphics.Bitmap.Config.ARGB_8888
             )
             val canvas = android.graphics.Canvas(bitmap)
             drawable.setBounds(0, 0, canvas.width, canvas.height)
             drawable.draw(canvas)
             bitmap
        }
    }
    companion object {
        const val CHANNEL_STATUS = "server_status"
        const val CHANNEL_PLAYERS = "player_events"
        const val CHANNEL_PERFORMANCE = "performance_alerts"
        const val CHANNEL_SETUP = "setup_progress"
    }

    init {
        createChannels()
    }

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS,
                "Status do Servidor",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificacoes de servidor ligado/desligado"
            }

            val playersChannel = NotificationChannel(
                CHANNEL_PLAYERS,
                "Eventos de Jogadores",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificacoes de entrada/saida de jogadores"
            }

            val perfChannel = NotificationChannel(
                CHANNEL_PERFORMANCE,
                "Alertas de Desempenho",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Alertas de uso critico de CPU e RAM"
            }

            val setupChannel = NotificationChannel(
                CHANNEL_SETUP,
                "Configuracao Inicial",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Progresso de download e instalacao"
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannels(listOf(statusChannel, playersChannel, perfChannel, setupChannel))
        }
    }

    fun showNotification(channelId: String, id: Int, title: String, message: String) {
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(getAppIconInfo())
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try { notify(id, builder.build()) } catch (e: SecurityException) { e.printStackTrace() }
        }
    }

    fun showSetupNotification(id: Int, title: String, status: String, progress: Int, max: Int = 100) {
        val remoteViews = android.widget.RemoteViews(context.packageName, R.layout.notification_setup)
        remoteViews.setTextViewText(R.id.notif_title, title)
        remoteViews.setTextViewText(R.id.notif_status, status)
        remoteViews.setProgressBar(R.id.notif_progress, max, progress, false)
        
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        remoteViews.setTextViewText(R.id.notif_time, timeFormat.format(java.util.Date()))

        val builder = NotificationCompat.Builder(context, CHANNEL_SETUP)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setLargeIcon(getAppIconInfo())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews) // Expand view
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true) 
            .setOngoing(true)
            .setAutoCancel(false)

        with(NotificationManagerCompat.from(context)) {
             try { notify(id, builder.build()) } catch (e: SecurityException) { e.printStackTrace() }
        }
    }

    fun updateLiveStats(players: String, cpu: String, ram: String) {
        val message = "Jogadores: $players | CPU: $cpu | RAM: $ram"
        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(getAppIconInfo())
            .setContentTitle("Servidor em Execucao")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        with(NotificationManagerCompat.from(context)) {
            try { notify(1337, builder.build()) } catch (e: SecurityException) { e.printStackTrace() }
        }
    }

    fun cancelNotification(id: Int) {
        with(NotificationManagerCompat.from(context)) { cancel(id) }
    }
}
