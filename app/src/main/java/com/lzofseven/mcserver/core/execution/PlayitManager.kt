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
    
    private var pendingRestart = false
    
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

    private val _address = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = _address

    private fun patchBinary() {
        if (!playitBin.exists()) return
        try {
            val bytes = playitBin.readBytes()
            val target = "/etc/resolv.conf".toByteArray(Charsets.UTF_8)
            val replacement = "/proc/self/cwd/r".toByteArray(Charsets.UTF_8)
            
            var found = false
            for (i in 0 until bytes.size - target.size) {
                var match = true
                for (j in target.indices) {
                    if (bytes[i + j] != target[j]) {
                        match = false
                        break
                    }
                }
                if (match) {
                    for (j in replacement.indices) {
                        bytes[i + j] = replacement[j]
                    }
                    found = true
                }
            }
            
            if (found) {
                Log.i("PlayitManager", "Patching binary...")
                playitBin.writeBytes(bytes)
                playitBin.setExecutable(true)
                Log.i("PlayitManager", "Playit binary patched for Android DNS")
            } else {
                Log.d("PlayitManager", "Binary already patched or target not found")
            }
        } catch (e: Exception) {
            if (e.message?.contains("ETXTBSY") == true) {
                Log.w("PlayitManager", "Binary busy, skipping patch (likely already patched)")
            } else {
                Log.e("PlayitManager", "Failed to patch binary", e)
            }
        }
    }

    fun start() {
        Log.i("PlayitManager", "Start requested. current process: $process")
        if (process != null) {
            if (process?.isAlive == true) return
            process = null
        }
        
        scope.launch {
            Log.i("PlayitManager", "Ensuring binary...")
            if (!ensureBinary()) {
                Log.e("PlayitManager", "Binary check failed")
                return@launch
            }
            
            patchBinary()
            
            _status.value = "Starting..."
            try {
                _claimLink.value = null
                if (_address.value == null || _address.value?.contains("Capturando") == true) {
                    _address.value = null
                }
                
                // Use a stable path for the secret file in our files directory
                val secretFile = File(context.filesDir, "playit.toml")
                
                // Create the DNS config file 'r' in the working directory
                val rFile = File(context.filesDir, "r")
                rFile.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
                
                Log.i("PlayitManager", "Starting process with secret_path: ${secretFile.absolutePath}")
                
                val builder = ProcessBuilder(
                    playitBin.absolutePath, 
                    "--secret_path", secretFile.absolutePath,
                    "--stdout",
                    "start"
                )
                builder.directory(context.filesDir)
                builder.redirectErrorStream(true)
                
                val p = builder.start()
                process = p
                _status.value = "Running"
                Log.i("PlayitManager", "Process started successfully")
                
                // Watchdog: If no address found in 30s, it might be stuck or silent.
                scope.launch {
                    kotlinx.coroutines.delay(30000)
                    if (status.value == "Running" && _address.value == null && _claimLink.value == null) {
                        Log.w("PlayitManager", "No address found after 30s, attempting silent restart...")
                        pendingRestart = true
                        p.destroy()
                    }
                }
                
                try {
                    val reader = BufferedReader(InputStreamReader(p.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { rawLine ->
                            val logLine = stripAnsi(rawLine)
                            Log.d("PlayitManager", "LOG: $logLine")
                            _playitLogs.emit(logLine)
                            
                            // Parse claim link
                            if (logLine.contains("claim link", ignoreCase = true) || logLine.contains("setup", ignoreCase = true)) {
                                val link = if (logLine.contains("https://")) {
                                    "https://" + logLine.substringAfter("https://").substringBefore(" ").trim()
                                } else null
                                
                                if (link?.startsWith("https://playit.gg") == true) {
                                    _claimLink.value = link
                                }
                            }
                            
                            // Parse registered tunnels
                            if (logLine.contains("agent has 0 tunnels", ignoreCase = true) || logLine.contains("0 tunnels registered", ignoreCase = true)) {
                                _address.value = "Adicione um Túnel no Painel"
                            }
                            
                            // Parse address
                            if (logLine.contains(".playit.gg") || logLine.contains(".joinmc.link") || logLine.contains("address", ignoreCase = true)) {
                                val addr = extractAddress(logLine)
                                if (addr != null && addr != _address.value) {
                                    _address.value = addr
                                    Log.i("PlayitManager", "Address updated: $addr")
                                }
                            }
                            
                            if ((logLine.contains("tunnels registered", ignoreCase = true) || logLine.contains("agent has", ignoreCase = true)) 
                                && !logLine.contains(" 0 tunnels", ignoreCase = true)) {
                                if (_address.value == null || _address.value?.contains("Adicione") == true) {
                                    _address.value = "Túnel Ativo (Capturando...)"
                                }
                            }
                        }
                    }
                } catch (e: java.io.IOException) {
                   // Handle stream closed gracefully
                   if (process != null) Log.d("PlayitManager", "Reader closed: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e("PlayitManager", "Playit failed to start/run", e)
                _status.value = "Failed"
            } finally {
                Log.i("PlayitManager", "Cleaning up process...")
                val restart = pendingRestart
                pendingRestart = false
                stop()
                if (restart) {
                    Log.i("PlayitManager", "Restarting Playit...")
                    start()
                }
            }
        }
    }

    private fun extractAddress(line: String): String? {
        val patterns = listOf(
            Regex("([a-z0-9-]+\\.playit\\.gg(?::\\d+)?)", RegexOption.IGNORE_CASE),
            Regex("([a-z0-9-]+\\.joinmc\\.link(?::\\d+)?)", RegexOption.IGNORE_CASE),
            Regex("address:\\s*([a-z0-9.-]+(?:\\.[a-z]{2,})+(?::\\d+)?)", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(line)
            if (match != null) {
                val addr = if (match.groups.size > 1) match.groups[1]?.value else match.value.trim()
                // Avoid catching control server IPs (they don't have .playit.gg or .joinmc.link usually in this regex)
                if (addr != null && (addr.contains(".playit.gg") || addr.contains(".joinmc.link"))) {
                    return addr
                }
            }
        }
        return null
    }

    private fun stripAnsi(text: String): String {
        return text.replace("\u001B\\[[;\\d]*[A-Za-z]".toRegex(), "")
    }

    fun stop() {
        process?.destroy()
        process = null
        _status.value = "Stopped"
        _claimLink.value = null
        // Don't clear address here if we want to keep it displayed while stopping/restarting
        // _address.value = null 
    }
}
