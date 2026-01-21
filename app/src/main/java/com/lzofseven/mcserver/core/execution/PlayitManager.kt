package com.lzofseven.mcserver.core.execution

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayitManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val binDir = File(context.filesDir, "bin")
    private val playitBin = File(binDir, "playit")
    
    private var process: Process? = null
    private val _status = MutableStateFlow("Stopped")
    val status: StateFlow<String> = _status
    
    private val _claimLink = MutableStateFlow<String?>(null)
    val claimLink: StateFlow<String?> = _claimLink

    private val _playitLogs = MutableSharedFlow<String>(replay = 50)
    val playitLogs: SharedFlow<String> = _playitLogs

    init {
        if (!binDir.exists()) binDir.mkdirs()
    }

    suspend fun ensureBinary(): Boolean {
        if (playitBin.exists() && playitBin.canExecute()) return true
        
        _status.value = "Downloading..."
        try {
            val url = "https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-linux-aarch64"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                response.body?.let { body ->
                    FileOutputStream(playitBin).use { output ->
                        body.byteStream().copyTo(output)
                    }
                    playitBin.setExecutable(true)
                    _status.value = "Ready"
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("PlayitManager", "Failed to download playit binary", e)
            _status.value = "Download Failed"
        }
        return false
    }

    fun start() {
        if (process != null) return
        
        scope.launch {
            if (!ensureBinary()) return@launch
            
            _status.value = "Starting..."
            try {
                val builder = ProcessBuilder(playitBin.absolutePath, "--secret-path", File(context.filesDir, "playit_secret").absolutePath)
                builder.directory(context.filesDir)
                builder.redirectErrorStream(true)
                
                process = builder.start()
                _status.value = "Running"
                
                val reader = BufferedReader(InputStreamReader(process?.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { logLine ->
                        _playitLogs.emit(logLine)
                        
                        // Parse claim link
                        if (logLine.contains("claim link:")) {
                            val link = logLine.substringAfter("claim link:").trim()
                            _claimLink.value = link
                        }
                        
                        // Parse active tunnel address
                        if (logLine.contains("tunnel running at:")) {
                            // Extract address if needed
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayitManager", "Playit failed to start", e)
                _status.value = "Failed"
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        process?.destroy()
        process = null
        _status.value = "Stopped"
        _claimLink.value = null
    }
}
