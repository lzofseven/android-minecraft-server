package com.lzofseven.mcserver.core.execution

import com.lzofseven.mcserver.core.java.JavaVersionManager
import com.lzofseven.mcserver.data.local.entity.MCServerEntity
import com.lzofseven.mcserver.util.DownloadStatus
import com.lzofseven.mcserver.util.NotificationHelper
import com.lzofseven.mcserver.util.McVersionUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

@Singleton
class RealServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val javaManager: JavaVersionManager,
    private val phpManager: com.lzofseven.mcserver.core.php.PhpRuntimeManager,
    private val notificationHelper: NotificationHelper,
    private val serverRepository: com.lzofseven.mcserver.data.repository.ServerRepository
) {
    private val processes = ConcurrentHashMap<String, Process>()
    
    private val _runningServerCount = MutableStateFlow(0)
    val runningServerCount: StateFlow<Int> = _runningServerCount.asStateFlow()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Console Stream: Map<ServerID, Flow<LogLine>>
    private val _consoleStreams = ConcurrentHashMap<String, MutableSharedFlow<String>>()
    
    // Players Stream: Map<ServerID, Flow<List<String>>>
    private val _onlinePlayers = ConcurrentHashMap<String, MutableStateFlow<List<String>>>()

    private val _opsUpdateEvent = MutableSharedFlow<Unit>(replay = 1)
    val opsUpdateEvent: SharedFlow<Unit> = _opsUpdateEvent.asSharedFlow()

    fun getConsoleFlow(serverId: String): SharedFlow<String> {
        return _consoleStreams.getOrPut(serverId) { MutableSharedFlow(replay = 50) }.asSharedFlow()
    }
    
    fun getPlayerListFlow(serverId: String): StateFlow<List<String>> {
        return _onlinePlayers.getOrPut(serverId) { MutableStateFlow(emptyList()) }.asStateFlow()
    }

    fun logToConsole(serverId: String, message: String) {
        val flow = _consoleStreams.getOrPut(serverId) { MutableSharedFlow(replay = 50) }
        scope.launch {
            flow.emit(message)
        }
    }

    private fun addPlayer(serverId: String, playerName: String) {
        val flow = _onlinePlayers.getOrPut(serverId) { MutableStateFlow(emptyList()) }
        val current = flow.value.toMutableList()
        if (!current.contains(playerName)) {
            current.add(playerName)
            flow.value = current
        }
    }
    
    private fun removePlayer(serverId: String, playerName: String) {
        val flow = _onlinePlayers.getOrPut(serverId) { MutableStateFlow(emptyList()) }
        val current = flow.value.toMutableList()
        current.remove(playerName)
        flow.value = current
    }

    suspend fun startServer(server: MCServerEntity) {
        if (processes.containsKey(server.id)) return // Already running

        // Start Foreground Service to keep process alive
        com.lzofseven.mcserver.service.MinecraftService.start(context)

        _consoleStreams.getOrPut(server.id) { MutableSharedFlow(replay = 50) }
        
        val isPocketMine = server.type.equals("pocketmine", ignoreCase = true)
        val serverDir = File(server.path)
        
        val executable: File
        val commandPrefix: List<String>
        
        if (isPocketMine) {
            emitLog(server.id, "Checking PHP Runtime...")
            if (!phpManager.isPhpInstalled()) {
                emitLog(server.id, "Installing PHP for Android (ARM64)...")
                phpManager.installPhp().collect { status ->
                     // Log progress if needed
                }
            }
            executable = phpManager.getPhpExecutable()
            if (!executable.exists()) {
                emitLog(server.id, "Error: PHP binary not found.")
                stopServiceIfEmpty()
                return
            }
            
            val pharFile = File(serverDir, "PocketMine-MP.phar")
            if (!pharFile.exists()) {
                 emitLog(server.id, "Error: PocketMine-MP.phar not found.")
                 stopServiceIfEmpty()
                 return
            }
            
            commandPrefix = listOf(executable.absolutePath, "PocketMine-MP.phar")
            
        } else {
            // Java Check
            val javaVersion = getJavaVersionForMc(server.version)
            emitLog(server.id, "Checking Java $javaVersion...")
    
            if (!javaManager.isJavaInstalled(javaVersion)) {
                emitLog(server.id, "Installing Java $javaVersion runtime... This may take a while.")
                 javaManager.installJava(javaVersion).collect { status ->
                     if (status is DownloadStatus.Progress) {
                         // Optionally emit progress to console
                     }
                 }
            }
            
            executable = javaManager.getJavaExecutable(javaVersion)
            if (!executable.exists()) {
                emitLog(server.id, "Error: Java binary not found at ${executable.absolutePath}")
                stopServiceIfEmpty()
                return
            }
    
            emitLog(server.id, "Starting server with ${executable.name}...")
    
            // Ensure critical symlinks exist
            val libDir = File(executable.parentFile.parentFile, "lib")
            val libCppShared = File(libDir, "libc++_shared.so")
            if (!libCppShared.exists()) {
                try {
                    val systemLibCpp = if (File("/system/lib64/libc++.so").exists()) "/system/lib64/libc++.so" else "/system/lib/libc++.so"
                    android.system.Os.symlink(systemLibCpp, libCppShared.absolutePath)
                    android.util.Log.d("RealServerManager", "Created missing libc++ symlink: ${libCppShared.absolutePath}")
                } catch (e: Exception) {
                    android.util.Log.e("RealServerManager", "Failed to create runtime symlink", e)
                }
            }
            
            val serverJar = File(serverDir, "server.jar")
            
            if (!serverJar.exists()) {
                emitLog(server.id, "Error: server.jar not found in ${serverDir.absolutePath}")
                stopServiceIfEmpty()
                return
            }
    
            commandPrefix = listOf(
                executable.absolutePath,
                "-Xms${server.ramAllocationMB}M",
                "-Xmx${server.ramAllocationMB}M",
                "-Djna.tmpdir=${context.cacheDir.absolutePath}",
                "-Djava.io.tmpdir=${context.cacheDir.absolutePath}",
                "-jar",
                serverJar.absolutePath,
                "nogui"
            )
        }

        try {
            // Create PID file
            val pidFile = File(serverDir, "server.pid")
            
            val builder = ProcessBuilder(commandPrefix)
            builder.directory(serverDir)
            builder.redirectErrorStream(true)
            
            // Setup Environment
            val env = builder.environment()
            
            if (isPocketMine) {
                 env["PHP_BINARY"] = executable.absolutePath
            } else {
                val javaHome = executable.parentFile.parentFile
                val libPath = File(javaHome, "lib").absolutePath
                val appLibPath = context.applicationInfo.nativeLibraryDir
                val systemLibPath = "/system/lib64:/system/lib"
                
                val currentLd = env["LD_LIBRARY_PATH"] ?: ""
                env["LD_LIBRARY_PATH"] = "$libPath:$appLibPath:$systemLibPath:$currentLd"
            }
            env["HOME"] = serverDir.absolutePath

            val process = builder.start()
            processes[server.id] = process
            
            // Save PID
            val pid = getPid(process)
            if (pid != null) {
                pidFile.writeText(pid.toString())
            }
            
            emitLog(server.id, "Servidor iniciado com PID: $pid")
            _runningServerCount.value = processes.size
            startStatsPoller(server.id, process, server.ramAllocationMB / 1024.0)
            
            // 4. Consume Output
            scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { logLine ->
                            emitLog(server.id, logLine)
                            
                            // Parse 'joined the game'
                            val joinMatch = Regex(".*: (.*) joined the game").find(logLine)
                            if (joinMatch != null) {
                                val playerName = joinMatch.groupValues[1]
                                addPlayer(server.id, playerName)
                                // Player join notification disabled to reduce notification spam
                                // TODO: Re-enable when settings screen is implemented
                                // notificationHelper.showNotification(
                                //     NotificationHelper.CHANNEL_PLAYERS,
                                //     200 + playerName.hashCode(), 
                                //     "Jogador Conectado",
                                //     "$playerName entrou no servidor."
                                // )
                            }
                            
                            // Parse 'left the game'
                            val leaveMatch = Regex(".*: (.*) left the game").find(logLine)
                            if (leaveMatch != null) {
                                val playerName = leaveMatch.groupValues[1]
                                removePlayer(server.id, playerName)
                            }
                            
                            // Parse 'Made <player> a server operator' or 'Deopped <player>'
                            if (logLine.contains("Made") && logLine.contains("a server operator")) {
                                _opsUpdateEvent.emit(Unit)
                            }
                            if (logLine.contains("Deopped")) {
                                _opsUpdateEvent.emit(Unit)
                            }
                        }
                    }
                } catch (e: Exception) {
                    emitLog(server.id, "Stream closed: ${e.message}")
                } finally {
                    processes.remove(server.id)
                    _runningServerCount.value = processes.size
                    emitLog(server.id, "O processo do servidor foi encerrado.")
                    
                    // Clear player list
                    _onlinePlayers[server.id]?.value = emptyList()
                    
                    // Stop service if no processes running
                    if (processes.isEmpty()) {
                        com.lzofseven.mcserver.service.MinecraftService.stop(context)
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            emitLog(server.id, "Failed to start process: ${e.message}")
            if (processes.isEmpty()) {
                com.lzofseven.mcserver.service.MinecraftService.stop(context)
            }
        }
    }

    fun sendCommand(serverId: String, command: String) {
        val process = processes[serverId]
        if (process != null && process.isAlive) {
            scope.launch {
                try {
                    val writer = process.outputStream.bufferedWriter()
                    writer.write(command + "\n")
                    writer.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun stopServer(serverId: String) {
        val process = processes[serverId]
        if (process == null || !process.isAlive) {
            processes.remove(serverId)
            return
        }
        
        scope.launch {
            try {
                // Graceful stop attempt
                val writer = process.outputStream.bufferedWriter()
                writer.write("stop\n")
                writer.flush()
                
                // Wait up to 30 seconds for graceful exit
                val exited = withTimeoutOrNull(30000) {
                    while (process.isAlive) {
                        delay(500)
                    }
                    true
                }
                
                if (exited == null && process.isAlive) {
                    process.destroy() // Force kill if it takes too long
                }
            } catch (e: Exception) {
                process.destroy()
            } finally {
                processes.remove(serverId)
                _runningServerCount.value = processes.size
            }
        }
    }

    fun killServer(serverId: String) {
        processes[serverId]?.destroy()
        processes.remove(serverId)
        
        // Remove PID file
        scope.launch {
            val server = serverRepository.getServerById(serverId)
            if (server != null) {
                val pidFile = File(server.path, "server.pid")
                if (pidFile.exists()) pidFile.delete()
            }
        }
    }
    
    fun isRunning(serverId: String): Boolean {
        val process = processes[serverId]
        return process != null && process.isAlive
    }

    suspend fun checkProcessHealth(serverId: String): Boolean {
        val server = serverRepository.getServerById(serverId) ?: return false
        val pidFile = File(server.path, "server.pid")
        if (!pidFile.exists()) return false
        
        val pidStr = pidFile.readText().trim()
        val pid = pidStr.toLongOrNull() ?: return false
        
        val processStat = File("/proc/$pid/stat")
        if (!processStat.exists()) {
            pidFile.delete()
            return false
        }
        
        // Ensure it's not a zombie and is likely a Java process
        return try {
            val cmdline = File("/proc/$pid/cmdline").readText()
            if (cmdline.contains("java")) {
                // If it's running but not in our map, we might want to "re-adopt" it 
                // but for now just reporting health is enough to set the UI status.
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }


    data class ServerStats(val cpu: Double, val ram: Double)

    // Stats Stream: Map<ServerID, Flow<ServerStats>>
    private val _serverStats = ConcurrentHashMap<String, MutableSharedFlow<ServerStats>>()

    fun getServerStatsFlow(serverId: String): SharedFlow<ServerStats> {
        return _serverStats.getOrPut(serverId) { MutableSharedFlow(replay = 1) }.asSharedFlow()
    }

    private fun startStatsPoller(serverId: String, process: Process, ramLimitGb: Double) {
        scope.launch {
            val pid = getPid(process) ?: return@launch
            var lastCpuTime = readProcessCpuTime(pid)
            var lastWallTime = System.currentTimeMillis()
            
            while (processes.containsKey(serverId) && process.isAlive) {
                try {
                    kotlinx.coroutines.delay(5000) // Update every 5 seconds as user requested
                    
                    val currentCpuTime = readProcessCpuTime(pid)
                    val currentWallTime = System.currentTimeMillis()
                    
                    // Calculate CPU percentage
                    val cpuDelta = currentCpuTime - lastCpuTime
                    val wallDelta = currentWallTime - lastWallTime
                    val cpuPercent = if (wallDelta > 0) {
                        (cpuDelta.toDouble() / wallDelta.toDouble()) * 100.0
                    } else 0.0
                    
                    lastCpuTime = currentCpuTime
                    lastWallTime = currentWallTime
                    
                    // Read RAM from /proc/pid/status
                    val ramGb = readProcessRam(pid)
                    
                    val flow = _serverStats.getOrPut(serverId) { MutableSharedFlow(replay = 1) }
                    flow.emit(ServerStats(cpuPercent.coerceIn(0.0, 100.0), ramGb))

                    // Update live notification with professional stats
                    val playerCount = _onlinePlayers[serverId]?.value?.size ?: 0
                    
                    val maxPlayers = try {
                        val server = serverRepository.getServerById(serverId)
                        if (server != null) {
                            val propsFile = File(server.path, "server.properties")
                            if (propsFile.exists()) {
                                val props = java.util.Properties()
                                props.load(java.io.FileInputStream(propsFile))
                                props.getProperty("max-players", "20").toIntOrNull() ?: 20
                            } else 20
                        } else 20
                    } catch (e: Exception) { 20 }
                    
                    notificationHelper.updateLiveStats(
                        onlinePlayers = playerCount,
                        maxPlayers = maxPlayers,
                        cpu = "${cpuPercent.toInt()}%",
                        ram = "${String.format("%.1f", ramGb)}/${String.format("%.1f", ramLimitGb)}GB"
                    )
                    
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    private fun readProcessCpuTime(pid: Long): Long {
        // Read utime + stime from /proc/pid/stat (fields 14 and 15, 1-indexed)
        return try {
            val statFile = File("/proc/$pid/stat")
            if (!statFile.exists()) return 0L
            val content = statFile.readText()
            val parts = content.substringAfter(") ").split(" ")
            if (parts.size > 13) {
                val utime = parts[11].toLongOrNull() ?: 0L // Field 14 (0-indexed after splitting is 11)
                val stime = parts[12].toLongOrNull() ?: 0L // Field 15
                // Convert from clock ticks to milliseconds (assuming 100 ticks/sec = HZ)
                ((utime + stime) * 1000L) / 100L
            } else 0L
        } catch (e: Exception) { 0L }
    }
    
    private fun readProcessRam(pid: Long): Double {
        return try {
            val statusFile = File("/proc/$pid/status")
            if (!statusFile.exists()) return 0.0
            var rssKb = 0L
            statusFile.forEachLine { line ->
                if (line.startsWith("VmRSS:")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        rssKb = parts[1].toLongOrNull() ?: 0L
                    }
                }
            }
            rssKb / 1024.0 / 1024.0 // Convert to GB
        } catch (e: Exception) { 0.0 }
    }
    
    private fun getPid(process: Process): Long? {
         // Android N+ / Java 9+ supports process.pid() but we might be on older API or different impl
         // Try reflection for UNIXProcess
         return try {
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                 // Java 9+ logic if available via desugaring or higher minSdk
                 // process.pid() 
                 // But since we are targeting Android, let's trust toString() hack or field access
                 val field = process.javaClass.getDeclaredField("pid")
                 field.isAccessible = true
                 field.getLong(process)
             } else {
                 null
             }
         } catch (e: Exception) {
             null
         }
    }



    private fun emitLog(serverId: String, message: String) {
        val flow = _consoleStreams.getOrPut(serverId) { MutableSharedFlow(replay = 50) }
        scope.launch {
            // Strip ANSI codes to check for timestamp
            val cleanMessage = message.replace(Regex("\u001B\\[[;\\d]*m"), "")
            
            // Check if message effectively starts with [HH:mm:ss]
            // We use find() instead of matches() to avoid issues with trailing characters or whitespace
            // The regex looks for start of string, optional whitespace, optional ANSI, then [digits:digits:digits]
            val hasTimestamp = Regex("^\\s*\\[\\d{2}:\\d{2}:\\d{2}]").containsMatchIn(cleanMessage)
            
            if (hasTimestamp) {
                flow.emit(message)
            } else {
                val timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                flow.emit("[$timestamp] $message")
            }
        }
    }

    private fun getJavaVersionForMc(mcVersion: String): Int {
        return McVersionUtils.getRequiredJavaVersion(mcVersion)
    }

    private fun stopServiceIfEmpty() {
        if (processes.isEmpty()) {
            com.lzofseven.mcserver.service.MinecraftService.stop(context)
        }
    }
}
