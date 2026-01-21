package com.lzofseven.mcserver.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lzofseven.mcserver.core.java.JavaVersionManager
import com.lzofseven.mcserver.util.DownloadStatus
import com.lzofseven.mcserver.util.NotificationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class StartupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val javaVersionManager: JavaVersionManager,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_JAVA_VERSION = "java_version"
        const val NOTIFICATION_ID = 200
    }

    override suspend fun doWork(): Result {
        val javaVersion = inputData.getInt(KEY_JAVA_VERSION, 17) // Default to 17

        notificationHelper.showSetupNotification(
            NOTIFICATION_ID,
            "Iniciando Download",
            "Preparando ambiente Java $javaVersion...",
            0
        )

        try {
            javaVersionManager.installJava(javaVersion).collect { status ->
                when (status) {
                    is DownloadStatus.Started -> {
                         notificationHelper.showSetupNotification(
                            NOTIFICATION_ID,
                            "Baixando Java $javaVersion",
                            "Iniciando...",
                            0
                        )
                    }
                    is DownloadStatus.Progress -> {
                        notificationHelper.showSetupNotification(
                            NOTIFICATION_ID,
                            "Baixando Java $javaVersion",
                            "${status.percentage}% concluído",
                            status.percentage
                        )
                        setProgress(workDataOf("progress" to status.percentage))
                    }
                    is DownloadStatus.Finished -> {
                         notificationHelper.showSetupNotification(
                            NOTIFICATION_ID,
                            "Instalação Concluída",
                            "Java $javaVersion pronto para uso.",
                            100
                        )
                    }
                    is DownloadStatus.Error -> {
                         notificationHelper.showSetupNotification(
                            NOTIFICATION_ID,
                            "Erro na Instalação",
                            status.message,
                            0
                        )
                        // Don't throw immediately if we want to return failure gracefully?
                        // throwing inside collect stops collection.
                        throw Exception(status.message)
                    }
                }
            }
            
            return Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            notificationHelper.showSetupNotification(
                 NOTIFICATION_ID,
                 "Falha na Instalação",
                 e.toString(),
                 0
            )
            val errorMsg = e.message ?: e.toString()
            return Result.failure(workDataOf("error" to errorMsg))
        }
    }
}
