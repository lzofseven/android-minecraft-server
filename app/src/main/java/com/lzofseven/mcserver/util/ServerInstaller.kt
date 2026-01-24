package com.lzofseven.mcserver.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerInstaller @Inject constructor(
    private val client: OkHttpClient,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    fun downloadServer(url: String, path: String, fileName: String): Flow<DownloadStatus> = flow {
        val request = Request.Builder().url(url).build()
        
        try {
            emit(DownloadStatus.Started)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
                
                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()

                if (path.startsWith("content://")) {
                    val uri = android.net.Uri.parse(path)
                    val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                    val targetFileDoc = docFile?.findFile(fileName) ?: docFile?.createFile("application/java-archive", fileName)
                    
                    if (targetFileDoc == null) throw Exception("Could not create file in $path")

                    context.contentResolver.openOutputStream(targetFileDoc.uri)?.use { outputStream ->
                        val buffer = ByteArray(32768)
                        var bytesRead = 0L
                        var read: Int
                        
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) {
                                val progress = (bytesRead.toDouble() / totalBytes * 100).toInt()
                                emit(DownloadStatus.Progress(progress))
                            }
                        }
                    }
                    emit(DownloadStatus.Finished(File(path, fileName))) // Using path for ID/Reference
                } else {
                    val destFile = File(path, fileName)
                    destFile.parentFile?.mkdirs()
                    val outputStream = FileOutputStream(destFile)
                    
                    val buffer = ByteArray(32768)
                    var bytesRead = 0L
                    var read: Int
                    
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            val progress = (bytesRead.toDouble() / totalBytes * 100).toInt()
                            emit(DownloadStatus.Progress(progress))
                        }
                    }
                    outputStream.close()
                    emit(DownloadStatus.Finished(destFile))
                }
            }
        } catch (e: Exception) {
            emit(DownloadStatus.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun acceptEula(path: String): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        android.util.Log.d("ServerInstaller", "acceptEula called for path: $path")
        if (path.startsWith("content://")) {
            try {
                val uri = android.net.Uri.parse(path)
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                
                // Force fresh file creation
                val existing = docFile?.findFile("eula.txt")
                if (existing != null && existing.exists()) {
                     android.util.Log.d("ServerInstaller", "Deleting existing EULA file to ensure write access")
                     existing.delete()
                }

                val eulaDoc = docFile?.createFile("text/plain", "eula.txt")
                android.util.Log.d("ServerInstaller", "New eulaDoc created: ${eulaDoc?.uri}")
                
                if (eulaDoc != null) {
                    context.contentResolver.openFileDescriptor(eulaDoc.uri, "wt")?.use { pfd ->
                        FileOutputStream(pfd.fileDescriptor).use { fos ->
                            fos.write("eula=true\n".toByteArray())
                            fos.flush()
                            fos.fd.sync() // Force write to disk
                            android.util.Log.d("ServerInstaller", "Wrote eula=true using PFD and synced")
                        }
                    }
                    return@withContext true
                } else {
                    android.util.Log.e("ServerInstaller", "Failed to create eula.txt document after delete")
                    return@withContext false
                }
            } catch (e: Exception) {
                android.util.Log.e("ServerInstaller", "Error accepting EULA (SAF)", e)
                e.printStackTrace()
                return@withContext false
            }
        } else {
            try {
                val file = File(path, "eula.txt")
                if (file.exists()) file.delete() // Ensure fresh write
                
                if (!file.parentFile.exists()) {
                    file.parentFile.mkdirs()
                }
                FileOutputStream(file).use { fos ->
                    fos.write("eula=true\n".toByteArray())
                    fos.flush()
                    fos.fd.sync()
                }
                android.util.Log.d("ServerInstaller", "Wrote eula=true to File with sync: ${file.absolutePath}")
                return@withContext true
            } catch (e: Exception) {
                android.util.Log.e("ServerInstaller", "Error accepting EULA (File)", e)
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    suspend fun isEulaAccepted(path: String): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        android.util.Log.d("ServerInstaller", "isEulaAccepted checking path: $path")
        if (path.startsWith("content://")) {
             try {
                val uri = android.net.Uri.parse(path)
                val docFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                val eulaDoc = docFile?.findFile("eula.txt")
                android.util.Log.d("ServerInstaller", "eulaDoc for check: ${eulaDoc?.uri}")

                if (eulaDoc != null) {
                    context.contentResolver.openInputStream(eulaDoc.uri)?.use { 
                        val content = it.reader().readText()
                        android.util.Log.d("ServerInstaller", "EULA content: '$content'")
                        return@withContext content.contains("eula=true")
                    }
                }
                false
            } catch (e: Exception) {
                android.util.Log.e("ServerInstaller", "Error checking EULA (SAF)", e)
                false
            }
        } else {
            try {
                val eulaFile = File(path, "eula.txt")
                if (!eulaFile.exists()) {
                     android.util.Log.d("ServerInstaller", "EULA file not found at ${eulaFile.absolutePath}")
                     return@withContext false
                }
                val content = eulaFile.readText()
                android.util.Log.d("ServerInstaller", "EULA file content: '$content'")
                content.contains("eula=true")
            } catch (e: SecurityException) {
                android.util.Log.w("ServerInstaller", "Permission denied reading EULA (need SAF): ${e.message}")
                false
            } catch (e: java.io.FileNotFoundException) {
                android.util.Log.w("ServerInstaller", "EULA file access denied (need SAF): ${e.message}")
                false
            } catch (e: Exception) {
                android.util.Log.e("ServerInstaller", "Error checking EULA", e)
                false
            }
        }
    }
}

sealed class DownloadStatus {
    object Started : DownloadStatus()
    data class Progress(val percentage: Int) : DownloadStatus()
    data class Finished(val file: File) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}
