package com.lzofseven.mcserver.core.jar

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Downloads Minecraft server JARs from official sources
 * Supports both regular File paths and SAF URIs (content://)
 */
class ServerJarDownloader @Inject constructor(
    private val httpClient: OkHttpClient,
    private val context: Context
) {
    
    sealed class DownloadStatus {
        object Idle : DownloadStatus()
        data class Downloading(val progress: Int, val downloadedMB: Float, val totalMB: Float) : DownloadStatus()
        data class Verifying(val message: String) : DownloadStatus()
        data class Success(val filePath: String) : DownloadStatus()
        data class Failed(val error: String) : DownloadStatus()
    }
    
    companion object {
        private const val TAG = "JarDownloader"
        private const val PAPER_API = "https://api.papermc.io/v2"
        private const val MOJANG_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest.json"
    }
    
    /**
     * Download from PaperMC (recommended for better performance)
     */
    fun downloadPaper(version: String, outputPath: String): Flow<DownloadStatus> = flow {
        emit(DownloadStatus.Idle)
        Log.i(TAG, "Downloading Paper $version to: $outputPath")
        
        try {
            // 1. Get latest build number for this version
            emit(DownloadStatus.Verifying("Finding latest build..."))
            val buildNumber = getLatestPaperBuild(version)
            
            if (buildNumber == null) {
                emit(DownloadStatus.Failed("Version $version not found on PaperMC"))
                return@flow
            }
            
            Log.d(TAG, "Latest Paper build for $version: $buildNumber")
            
            // 2. Construct download URL
            val downloadUrl = "$PAPER_API/projects/paper/versions/$version/builds/$buildNumber/downloads/paper-$version-$buildNumber.jar"
            
            // 3. Download with progress
            val isSafUri = outputPath.startsWith("content://")
            val tempFileName = "server.jar.tmp"
            
            val request = Request.Builder()
                .url(downloadUrl)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadStatus.Failed("HTTP ${response.code}: ${response.message}"))
                    return@flow
                }
                
                val body = response.body
                if (body == null) {
                    emit(DownloadStatus.Failed("Empty response from server"))
                    return@flow
                }
                
                val totalBytes = body.contentLength()
                val totalMB = if (totalBytes > 0) totalBytes / 1_000_000f else 0f
                
                Log.d(TAG, "Downloading ${totalMB}MB...")
                
                // Create output stream (SAF or File)
                val outputStream: OutputStream
                val tempFile: Any // Either File or DocumentFile
                
                if (isSafUri) {
                    // SAF path - use DocumentFile
                    val treeUri = Uri.parse(outputPath)
                    val docDir = DocumentFile.fromTreeUri(context, treeUri)
                        ?: throw Exception("Cannot access SAF directory")
                    
                    // Delete existing temp file if any
                    docDir.findFile(tempFileName)?.delete()
                    
                    val tempDoc = docDir.createFile("application/java-archive", tempFileName)
                        ?: throw Exception("Cannot create temp file")
                    
                    outputStream = context.contentResolver.openOutputStream(tempDoc.uri)
                        ?: throw Exception("Cannot open output stream")
                    tempFile = tempDoc
                    
                } else {
                    // Regular File path
                    val dir = File(outputPath)
                    val file = File(dir, tempFileName)
                    if (file.exists()) file.delete()
                    
                    outputStream = FileOutputStream(file)
                    tempFile = file
                }
                
                outputStream.use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int
                        var lastProgress = 0
                        
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            
                            val progress = if (totalBytes > 0) {
                                ((downloaded * 100) / totalBytes).toInt()
                            } else {
                                -1 // Indeterminate
                            }
                            
                            // Only emit every 5% to avoid flooding
                            if (progress >= 0 && (progress - lastProgress >= 5 || progress == 100)) {
                                val downloadedMB = downloaded / 1_000_000f
                                emit(DownloadStatus.Downloading(progress, downloadedMB, totalMB))
                                lastProgress = progress
                            } else if (progress == -1 && downloaded % 500_000 == 0L) {
                                // Emit periodically for indeterminate downloads
                                val downloadedMB = downloaded / 1_000_000f
                                emit(DownloadStatus.Downloading(0, downloadedMB, 0f))
                            }
                        }
                    }
                }
            }
            
            // Return success with temp file reference
            if (isSafUri) {
                val treeUri = Uri.parse(outputPath)
                val docDir = DocumentFile.fromTreeUri(context, treeUri)!!
                val tempDoc = docDir.findFile(tempFileName)!!
                emit(DownloadStatus.Success(tempDoc.uri.toString()))
            } else {
                val dir = File(outputPath)
                val tempFile = File(dir, tempFileName)
                emit(DownloadStatus.Success(tempFile.absolutePath))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            emit(DownloadStatus.Failed("Download error: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Download vanilla server from Mojang (fallback)
     */
    fun downloadVanilla(version: String, outputPath: String): Flow<DownloadStatus> = flow {
        emit(DownloadStatus.Idle)
        Log.i(TAG, "Downloading vanilla $version to: $outputPath")
        
        try {
            // 1. Get version manifest
            emit(DownloadStatus.Verifying("Fetching Mojang manifest..."))
            val manifestUrl = getVanillaManifestUrl(version)
            
            if (manifestUrl == null) {
                emit(DownloadStatus.Failed("Version $version not found"))
                return@flow
            }
            
            // 2. Get server download URL from version manifest
            emit(DownloadStatus.Verifying("Getting server download URL..."))
            val serverUrl = getVanillaServerUrl(manifestUrl)
            
            if (serverUrl == null) {
                emit(DownloadStatus.Failed("Server JAR URL not found"))
                return@flow
            }
            
            // 3. Download server JAR
            val isSafUri = outputPath.startsWith("content://")
            val tempFileName = "server.jar.tmp"
            
            val request = Request.Builder()
                .url(serverUrl)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadStatus.Failed("HTTP ${response.code}"))
                    return@flow
                }
                
                val body = response.body ?: run {
                    emit(DownloadStatus.Failed("Empty response"))
                    return@flow
                }
                
                val totalBytes = body.contentLength()
                val totalMB = totalBytes / 1_000_000f
                
                // Create output stream (SAF or File)
                val outputStream: OutputStream
                
                if (isSafUri) {
                    val treeUri = Uri.parse(outputPath)
                    val docDir = DocumentFile.fromTreeUri(context, treeUri)
                        ?: throw Exception("Cannot access SAF directory")
                    
                    docDir.findFile(tempFileName)?.delete()
                    
                    val tempDoc = docDir.createFile("application/java-archive", tempFileName)
                        ?: throw Exception("Cannot create temp file")
                    
                    outputStream = context.contentResolver.openOutputStream(tempDoc.uri)
                        ?: throw Exception("Cannot open output stream")
                } else {
                    val dir = File(outputPath)
                    val file = File(dir, tempFileName)
                    if (file.exists()) file.delete()
                    
                    outputStream = FileOutputStream(file)
                }
                
                outputStream.use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int
                        var lastProgress = 0
                        
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            
                            val progress = ((downloaded * 100) / totalBytes).toInt()
                            if (progress - lastProgress >= 5 || progress == 100) {
                                val downloadedMB = downloaded / 1_000_000f
                                emit(DownloadStatus.Downloading(progress, downloadedMB, totalMB))
                                lastProgress = progress
                            }
                        }
                    }
                }
            }
            
            // Return success with temp file reference
            if (isSafUri) {
                val treeUri = Uri.parse(outputPath)
                val docDir = DocumentFile.fromTreeUri(context, treeUri)!!
                val tempDoc = docDir.findFile(tempFileName)!!
                emit(DownloadStatus.Success(tempDoc.uri.toString()))
            } else {
                val dir = File(outputPath)
                val tempFile = File(dir, tempFileName)
                emit(DownloadStatus.Success(tempFile.absolutePath))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Vanilla download failed", e)
            emit(DownloadStatus.Failed("Download error: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Get latest Paper build number for a version
     */
    private fun getLatestPaperBuild(version: String): Int? {
        return try {
            val url = "$PAPER_API/projects/paper/versions/$version"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) return null
            
            val json = JSONObject(response.body?.string() ?: "{}")
            val builds = json.getJSONArray("builds")
            
            if (builds.length() > 0) {
                builds.getInt(builds.length() - 1)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Paper build", e)
            null
        }
    }
    
    /**
     * Get version manifest URL from Mojang
     */
    private fun getVanillaManifestUrl(version: String): String? {
        return try {
            val request = Request.Builder().url(MOJANG_MANIFEST).build()
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) return null
            
            val json = JSONObject(response.body?.string() ?: "{}")
            val versions = json.getJSONArray("versions")
            
            for (i in 0 until versions.length()) {
                val versionObj = versions.getJSONObject(i)
                if (versionObj.getString("id") == version) {
                    return versionObj.getString("url")
                }
            }
            
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting vanilla manifest", e)
            null
        }
    }
    
    /**
     * Get server JAR URL from version manifest
     */
    private fun getVanillaServerUrl(manifestUrl: String): String? {
        return try {
            val request = Request.Builder().url(manifestUrl).build()
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) return null
            
            val json = JSONObject(response.body?.string() ?: "{}")
            val downloads = json.getJSONObject("downloads")
            val server = downloads.getJSONObject("server")
            
            server.getString("url")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting server URL", e)
            null
        }
    }
}
