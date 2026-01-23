package com.lzofseven.mcserver.core.jar

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.documentfile.provider.DocumentFile
import javax.inject.Inject

/**
 * Coordinates automatic JAR repair process
 */
class ServerJarRepairManager @Inject constructor(
    private val context: Context,
    private val validator: JarValidator,
    private val versionDetector: ServerVersionDetector,
    private val downloader: ServerJarDownloader
) {
    
    sealed class RepairStatus {
        object Validating : RepairStatus()
        object DetectingVersion : RepairStatus()
        data class Downloading(val progress: Int, val downloadedMB: Float, val totalMB: Float) : RepairStatus()
        object BackingUp : RepairStatus()
        object Replacing : RepairStatus()
        data class Success(val version: String, val type: String) : RepairStatus()
        data class Failed(val reason: String, val canRetry: Boolean) : RepairStatus()
    }
    
    companion object {
        private const val TAG = "JarRepairManager"
    }
    
    /**
     * Check if JAR needs repair and fix it automatically if needed
     * 
     * @param serverPath Path or SAF URI of the server directory
     * @param serverName Name of the server (for version detection)
     * @param serverVersion Optional version if already known
     * @return Flow of repair status updates, null if no repair needed
     */
    fun repairIfNeeded(
        serverPath: String,
        serverName: String,
        serverVersion: String? = null,
        serverType: String? = null
    ): Flow<RepairStatus>? {
        // SAFETY: Do not attempt to "repair" (replace with Paper/Vanilla) if it's a modded server.
        // Fabric/Forge jars are often custom or small wrappers that validator might flag incorrectly.
        // Even if we fixed the validator, we must ensuring we never overwrite them with vanilla/paper by mistake.
        if (serverType != null) {
            val type = serverType.lowercase()
            if (type.contains("fabric") || type.contains("forge")) {
                 Log.w(TAG, "Skipping auto-repair for modded server type: $type (Risk of overwriting mod loader)")
                 return null
            }
        }

        // Validation check depends on whether it's SAF or direct File
        // Validation check depends on whether it's SAF or direct File
        val validationResult = if (serverPath.startsWith("content://")) {
            try {
                val treeUri = Uri.parse(serverPath)
                val docDir = DocumentFile.fromTreeUri(context, treeUri)
                val jarDoc = docDir?.findFile("server.jar")
                validator.validateJar(jarDoc)
            } catch (e: Exception) {
                JarValidator.ValidationResult.Invalid("SAF access error: ${e.message}")
            }
        } else {
            val jarFile = File(serverPath, "server.jar")
            validator.validateJar(jarFile)
        }

        Log.d(TAG, "Running validation for: $serverPath")
        if (validationResult is JarValidator.ValidationResult.Invalid || 
            validationResult is JarValidator.ValidationResult.Corrupted) {
            Log.i(TAG, "Validation failed: $validationResult - Initiating repair for $serverName")
            return performRepair(serverPath, serverName, serverVersion, serverType)
        }
        
        Log.d(TAG, "JAR is valid, skipping repair")
        return null
    }
    
    /**
     * Perform the actual repair process
     */
    private fun performRepair(
        serverPath: String,
        serverName: String,
        serverVersion: String? = null,
        serverType: String? = null
    ): Flow<RepairStatus> = flow {
        try {
            // 1. Detect version (SAF-aware)
            emit(RepairStatus.DetectingVersion)
            val detected = versionDetector.detectVersion(context, serverPath, serverName, serverVersion)
            
            if (detected == null) {
                emit(RepairStatus.Failed(
                    "Cannot determine Minecraft version. Please check server configuration or recreate the server.",
                    canRetry = false
                ))
                return@flow
            }
            
            // 3. Download replacement JAR
            emit(RepairStatus.Downloading(0, 0f, 0f))
            
            var downloadedTempPath: String? = null
            var downloadFailed = false
            var failureReason = ""
            
            // Download based on type, fallback only if specifically allowed or requested
            val type = serverType?.lowercase() ?: detected.type.name.lowercase()
            
            suspend fun tryDownload(currentType: String): Boolean {
                 var success = false
                 val dFlow = when (currentType) {
                     "paper" -> downloader.downloadPaper(detected.version, serverPath)
                     "fabric" -> downloader.downloadFabric(detected.version, serverPath)
                     else -> downloader.downloadVanilla(detected.version, serverPath)
                 }
                 
                 dFlow.collect { status ->
                    when (status) {
                        is ServerJarDownloader.DownloadStatus.Downloading -> {
                            emit(RepairStatus.Downloading(status.progress, status.downloadedMB, status.totalMB))
                        }
                        is ServerJarDownloader.DownloadStatus.Success -> {
                            downloadedTempPath = status.filePath
                            success = true
                        }
                        is ServerJarDownloader.DownloadStatus.Failed -> {
                            Log.w(TAG, "$currentType download failed: ${status.error}")
                            failureReason = status.error
                        }
                        else -> {}
                    }
                }
                return success
            }

            if (!tryDownload(type)) {
                // If requested type failed, try Vanilla as absolute last resort only if it wasn't already tried
                if (type != "vanilla") {
                    Log.i(TAG, "Requested engine $type failed, trying Vanilla fallback...")
                    if (!tryDownload("vanilla")) {
                        downloadFailed = true
                    }
                } else {
                    downloadFailed = true
                }
            }
            
            if (downloadFailed) {
                emit(RepairStatus.Failed(failureReason, canRetry = true))
                return@flow
            }
            
            val tempPath = downloadedTempPath ?: return@flow
            
            // 4. Backup and Replace (SAF-aware)
            emit(RepairStatus.Replacing)
            
            if (serverPath.startsWith("content://")) {
                // SAF Path
                val treeUri = Uri.parse(serverPath)
                val docDir = DocumentFile.fromTreeUri(context, treeUri)
                    ?: throw Exception("Cannot access SAF directory")
                
                var jarDoc = docDir.findFile("server.jar")
                if (jarDoc == null) {
                    jarDoc = docDir.createFile("application/java-archive", "server.jar")
                        ?: throw Exception("Cannot create server.jar in SAF")
                }
                
                val tempUri = Uri.parse(tempPath)
                context.contentResolver.openOutputStream(jarDoc.uri)?.use { output ->
                    context.contentResolver.openInputStream(tempUri)?.use { input ->
                        input.copyTo(output)
                    }
                }
                // Optional: delete temp file if it's SAF or regular file
                try {
                   if (tempPath.startsWith("content://")) {
                       DocumentFile.fromSingleUri(context, tempUri)?.delete()
                   } else {
                       File(tempPath).delete()
                   }
                } catch (e: Exception) {}
            } else {
                // Regular File Path
                val targetFile = File(serverPath, "server.jar")
                if (targetFile.exists()) {
                    val backup = File(targetFile.parent, "server.jar.backup")
                    targetFile.renameTo(backup)
                }
                File(tempPath).copyTo(targetFile, overwrite = true)
                File(tempPath).delete()
            }
            
            emit(RepairStatus.Success(detected.version, detected.type.name))
            
        } catch (e: Exception) {
            Log.e(TAG, "Repair failed", e)
            emit(RepairStatus.Failed("Repair error: ${e.message}", true))
        }
    }
}
