package com.lzofseven.mcserver.core.php

import android.content.Context
import com.lzofseven.mcserver.util.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
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
class PhpRuntimeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    private val runtimesDir = File(context.filesDir, "runtimes")
    private val phpDir = File(runtimesDir, "php")
    private val phpBin = File(phpDir, "bin/php")

    init {
        if (!phpDir.exists()) phpDir.mkdirs()
    }

    fun getPhpExecutable(): File {
        return phpBin
    }

    fun isPhpInstalled(): Boolean {
        // Basic check: exists, executable, and reasonably sized (>1MB)
        return phpBin.exists() && phpBin.canExecute() && phpBin.length() > 1024 * 1024
    }

    fun installPhp(): Flow<DownloadStatus> = flow {
        if (isPhpInstalled()) {
            emit(DownloadStatus.Finished(phpDir))
            return@flow
        }

        val binDir = File(phpDir, "bin")
        if (!binDir.exists()) binDir.mkdirs()

        // Using a pre-compiled PHP 8.x for Android aarch64
        // Source: https://github.com/ItzxDwi/AndroidPHP (or similar reliable source)
        // For stability, we might want to mirror this or use a specific release.
        // This URL is a direct binary download.
        val downloadUrl = "https://github.com/ItzxDwi/AndroidPHP/releases/latest/download/php"

        emit(DownloadStatus.Started)

        try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("Failed to download PHP: ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength()
            
            val tempFile = File(context.cacheDir, "php_temp")
            
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead = 0L
                    var read: Int
                    
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            emit(DownloadStatus.Progress(((bytesRead * 100) / totalBytes).toInt()))
                        }
                    }
                }
            }
            
            // Move to final location
            if (phpBin.exists()) phpBin.delete()
            tempFile.copyTo(phpBin, overwrite = true)
            tempFile.delete()
            
            // Make executable
            phpBin.setExecutable(true)
            
            // Also need to ensure internal structure if it expects specific libs or ini
            // The single binary usually handles most for PocketMine if statically compiled.
            // If it needs a specific php.ini, we should create a default one.
            createDefaultPhpIni(binDir)

            emit(DownloadStatus.Finished(phpDir))

        } catch (e: Exception) {
            e.printStackTrace()
            emit(DownloadStatus.Error("PHP Install Failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    private fun createDefaultPhpIni(binDir: File) {
        val iniFile = File(binDir, "php.ini")
        if (!iniFile.exists()) {
            val content = """
                date.timezone = "UTC"
                short_open_tag = Off
                asp_tags = Off
                phar.readonly = Off
                memory_limit = 512M
                display_errors = On
                display_startup_errors = On
            """.trimIndent()
            iniFile.writeText(content)
        }
    }
}
