package com.lzofseven.mcserver.core.execution

import com.lzofseven.mcserver.core.java.JavaVersionManager
import com.lzofseven.mcserver.core.jar.JarValidator
import com.lzofseven.mcserver.core.jar.ServerVersionDetector
import com.lzofseven.mcserver.core.jar.ServerJarDownloader
import com.lzofseven.mcserver.core.jar.ServerJarRepairManager
import com.lzofseven.mcserver.data.local.entity.MCServerEntity
import com.lzofseven.mcserver.util.DownloadStatus
import com.lzofseven.mcserver.util.NotificationHelper
import com.lzofseven.mcserver.util.McVersionUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.jar.JarInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import android.util.Log
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

@Singleton
class RealServerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val javaManager: JavaVersionManager,
    private val notificationHelper: NotificationHelper,
    private val serverRepository: com.lzofseven.mcserver.data.repository.ServerRepository
) {

    private val processes = ConcurrentHashMap<String, Process>()
    
    // HTTP client for JAR downloads
    private val serviceHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
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
        
        // CLEANUP: Kill any zombie processes for this server ID before starting
        cleanupOrphanedProcesses(server.id)

        // Always false now
        val isPocketMine = false 
        
        // Path Resolution Logic
        val serverPath = server.uri ?: server.path
        val serverDir = if (serverPath.startsWith("content://")) null else File(serverPath)

        val executable: File
        val commandPrefix: List<String>
        
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

            // NEW: Fix for libandroid-shmem.so not found by libjvm.so
            // libjvm.so is in lib/server/ and expects libandroid-shmem.so to be findable.
            val libServerDir = File(libDir, "server")
            val libShmem = File(libDir, "libandroid-shmem.so")
            val libShmemLink = File(libServerDir, "libandroid-shmem.so")
            
            if (libServerDir.exists() && libShmem.exists() && !libShmemLink.exists()) {
                 try {
                     // Create a symlink in lib/server/ pointing up to ../libandroid-shmem.so
                     // or just absolute path symlink
                     android.system.Os.symlink(libShmem.absolutePath, libShmemLink.absolutePath)
                     Log.d("RealServerManager", "Created libandroid-shmem.so symlink in server dir")
                 } catch (e: Exception) {
                     Log.e("RealServerManager", "Failed to create shmem symlink", e)
                 }
            }
            
            // NEW: Path resolution for SAF (Java needs a real filesystem path)
            var effectiveServerPath = serverPath
            var effectiveJarPath: String
            
            if (serverPath.startsWith("content://")) {
                val resolvedPath = getRealPathFromSaf(serverPath)
                if (resolvedPath != null) {
                    Log.i("RealServerManager", "Successfully resolved SAF to real path: $resolvedPath")
                    effectiveServerPath = resolvedPath
                    effectiveJarPath = File(resolvedPath, "server.jar").absolutePath
                } else {
                    Log.w("RealServerManager", "Could not resolve SAF to real path. Java may fail.")
                    // Fallback to original serverJarPathStr logic if resolution fails
                    effectiveJarPath = if (serverPath.endsWith("/")) serverPath + "server.jar" else "$serverPath/server.jar"
                }
            } else {
                effectiveJarPath = File(serverDir!!, "server.jar").absolutePath
            }

            // For execution, we use the resolved paths
            val serverJarPathForCommand = effectiveJarPath
            
            // NEW: Auto-repair corrupted JARs
            Log.d("RealServerManager", "Checking JAR repair for path: $serverPath")
            val jarRepairManager = ServerJarRepairManager(
                context,
                JarValidator(),
                ServerVersionDetector(),
                ServerJarDownloader(serviceHttpClient, context)
            )
            
            val repairFlow = jarRepairManager.repairIfNeeded(serverPath, server.name, server.version, server.type.lowercase())
            if (repairFlow != null) {
                Log.i("RealServerManager", "Initiating repair flow for ${server.name}")
                emitLog(server.id, "⚠️ Invalid JAR detected, initiating auto-repair...")
                
                var repairFailed = false
                
                repairFlow.collect { repairStatus ->
                    when (repairStatus) {
                        is ServerJarRepairManager.RepairStatus.Validating -> {
                            emitLog(server.id, "Validating JAR integrity...")
                        }
                        is ServerJarRepairManager.RepairStatus.DetectingVersion -> {
                            emitLog(server.id, "Detecting Minecraft version...")
                        }
                        is ServerJarRepairManager.RepairStatus.Downloading -> {
                            val percent = repairStatus.progress
                            val mb = "%.1f".format(repairStatus.downloadedMB)
                            val totalMb = "%.1f".format(repairStatus.totalMB)
                            emitLog(server.id, "Downloading server JAR: $percent% ($mb/$totalMb MB)")
                        }
                        is ServerJarRepairManager.RepairStatus.BackingUp -> {
                            emitLog(server.id, "Backing up old JAR...")
                        }
                        is ServerJarRepairManager.RepairStatus.Replacing -> {
                            emitLog(server.id, "Installing new JAR...")
                        }
                        is ServerJarRepairManager.RepairStatus.Success -> {
                            emitLog(server.id, "✅ JAR repaired successfully! (${repairStatus.type} ${repairStatus.version})")
                        }
                        is ServerJarRepairManager.RepairStatus.Failed -> {
                            emitLog(server.id, "❌ JAR repair failed: ${repairStatus.reason}")
                            repairFailed = true
                        }
                    }
                }
                
                if (repairFailed) {
                    stopServiceIfEmpty()
                    return
                }
            }
            
            val jarExists = if (serverPath.startsWith("content://")) {
                try {
                    val treeUri = Uri.parse(serverPath)
                    val docDir = DocumentFile.fromTreeUri(context, treeUri)
                    val found = docDir?.findFile("server.jar")
                    Log.d("RealServerManager", "SAF check: docDir=$docDir, jarFound=${found?.exists()}")
                    found?.exists() == true
                } catch (e: Exception) { 
                    Log.e("RealServerManager", "SAF check failed", e)
                    false 
                }
            } else {
                val exists = File(serverPath, "server.jar").exists()
                Log.d("RealServerManager", "File check: exists=$exists")
                exists
            }

            if (!jarExists) {
                Log.e("RealServerManager", "Critical: server.jar not found even after repair check!")
                emitLog(server.id, "Error: server.jar not found in $serverPath")
                stopServiceIfEmpty()
                return
            }
            
            // Read CPU Core Limit from config
            val configFile = File(effectiveServerPath, "manager_config.properties")
            var cpuCores = Runtime.getRuntime().availableProcessors()
            if (configFile.exists()) {
                try {
                    val props = java.util.Properties()
                    configFile.inputStream().use { props.load(it) }
                    cpuCores = props.getProperty("cpuCores", cpuCores.toString()).toIntOrNull() ?: cpuCores
                } catch (e: Exception) { e.printStackTrace() }
            }

            // Build command with Java-version-specific flags
            // NEW: SAF Execution Strategy - "Copy-to-Run"
            // Android prevents executing binaries/JARs directly from /storage/emulated/0 via Runtime.exec()
            // even with permissions. We must copy the JAR to a private, executable directory.
            val executionDir = File(context.filesDir, "server_execution_${server.id}")
            if (!executionDir.exists()) executionDir.mkdirs()
            
            val privateJarFile = File(executionDir, "server.jar")
            
            // Files to copy from source to execution dir
            val filesToCopy = listOf("server.jar", "server.properties", "eula.txt", "banned-ips.json", "banned-players.json", "ops.json", "whitelist.json")

            if (serverPath.startsWith("content://")) {
                Log.i("RealServerManager", "SAF Mode: Copying server files to private execution directory...")
                val treeUri = Uri.parse(serverPath)
                val docDir = DocumentFile.fromTreeUri(context, treeUri)
                
                if (docDir != null) {
                    filesToCopy.forEach { fileName ->
                        val sourceFile = docDir.findFile(fileName)
                        if (sourceFile != null && sourceFile.exists()) {
                            try {
                                val destFile = File(executionDir, fileName)
                                context.contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("RealServerManager", "Failed to copy $fileName: ${e.message}")
                            }
                        }
                    }
                }
            } else {
                 // Direct file copy
                 val sourceDir = File(effectiveServerPath)
                 filesToCopy.forEach { fileName ->
                     val sourceFile = File(sourceDir, fileName)
                     if (sourceFile.exists()) {
                         try {
                              sourceFile.copyTo(File(executionDir, fileName), overwrite = true)
                         } catch (e: Exception) {
                              Log.w("RealServerManager", "Failed to copy $fileName: ${e.message}")
                         }
                     }
                 }
            }
            
            // FORCE ACCEPT EULA in execution dir (since user likely accepted in UI)
            val eulaFile = File(executionDir, "eula.txt")
            if (!eulaFile.exists() || !eulaFile.readText().contains("eula=true")) {
                eulaFile.writeText("eula=true\n")
            }
            
            // Build command with Java-version-specific flags
            
            // Build command with Java-version-specific flags
            // Build command with Java-version-specific flags
            val baseCommand = mutableListOf(
                executable.absolutePath,
                "-Xms${server.ramAllocationMB}M",
                "-Xmx${server.ramAllocationMB}M",
                "-Djna.tmpdir=${context.cacheDir.absolutePath}",
                "-Djava.io.tmpdir=${context.cacheDir.absolutePath}",
                "-Duser.dir=${executionDir.absolutePath}" // Force working dir property
            )

            // CACHED PATCHED JAR CHECK
            // If the server has already been patched (e.g. by a previous run or manual patch),
            // prefer the patched JAR. This bypasses the need for the agent entirely.
            val cacheDir = File(executionDir, "cache")
            val patchedJar = cacheDir.listFiles { _, name -> 
                name.startsWith("patched_") && name.endsWith(".jar") 
            }?.firstOrNull()

            if (patchedJar != null && patchedJar.exists()) {
                Log.i("RealServerManager", "Found pre-patched JAR: ${patchedJar.name}. Using it directly.")
                baseCommand.add("-jar")
                baseCommand.add(patchedJar.absolutePath)
                baseCommand.add("nogui")
            } else {
                // AGENT BYPASS STRATEGY:
                // Check for Main-Class in manifest to run with -cp instead of -jar.
                // This avoids the JVM trying to load the "Launcher-Agent-Class" (Paperclip)
                // which requires libinstrument.so and libiconv.so (missing on Android).
                val mainClass = getMainClass(privateJarFile)
                if (mainClass != null) {
                    Log.i("RealServerManager", "Agent Bypass: Found Main-Class '$mainClass', using -cp strategy.")
                    baseCommand.add("-cp")
                    baseCommand.add(privateJarFile.absolutePath)
                    baseCommand.add(mainClass)
                    baseCommand.add("nogui")
                } else {
                    Log.w("RealServerManager", "No Main-Class found, falling back to -jar (Risk of agent crash).")
                    baseCommand.add("-jar")
                    baseCommand.add(privateJarFile.absolutePath)
                    baseCommand.add("nogui")
                }
            }
            
            Log.i("RealServerManager", "Executing from private dir: ${baseCommand.joinToString(" ")}")
            
            commandPrefix = baseCommand


        try {
            // Correct working directory for the process
            val workingDir = if (commandPrefix[0].contains("php")) {
                // PocketMine handling (already logic for this above)
                serverDir!! 
            } else {
                 val path = if (serverPath.startsWith("content://")) {
                     getRealPathFromSaf(serverPath)?.let { File(it) } ?: context.cacheDir
                 } else {
                     File(serverPath)
                 }
                 path
            }
            val executionDir = File(context.filesDir, "server_execution_${server.id}")
            // Create PID file in the execution dir
            val pidFile = File(executionDir, "server.pid")
            
            val builder = ProcessBuilder(commandPrefix)
            builder.directory(executionDir) // Set working directory to private dir
            builder.redirectErrorStream(false) // Handle stderr separately to catch early crashes
            
            Log.d("RealServerManager", "Working directory: ${executionDir.absolutePath}")
            
            // Setup Environment
            val env = builder.environment()
            
            if (isPocketMine) {
                 env["PHP_BINARY"] = executable.absolutePath
            } else {
                val javaHome = executable.parentFile.parentFile
                val libPath = File(javaHome, "lib").absolutePath
                val libServerPath = File(File(javaHome, "lib"), "server").absolutePath
                val appLibPath = context.applicationInfo.nativeLibraryDir
                val systemLibPath = "/system/lib64:/system/lib"
                
                val currentLd = env["LD_LIBRARY_PATH"] ?: ""
                val newLd = "$libServerPath:$libPath:$appLibPath:$systemLibPath:$currentLd"
                env["LD_LIBRARY_PATH"] = newLd
                Log.d("RealServerManager", "LD_LIBRARY_PATH: $newLd")
            }
            env["HOME"] = serverDir?.absolutePath ?: context.filesDir.absolutePath

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
            val stderrScope = CoroutineScope(Dispatchers.IO + Job())
            
            // Standard Output Consumer
            scope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { logLine ->
                            Log.v("RealServerManager", "STDOUT: $logLine")
                            emitLog(server.id, logLine)
                            
                            // Parse 'joined the game'
                            val joinMatch = Regex(".*: (.*) joined the game").find(logLine)
                            if (joinMatch != null) {
                                val playerName = joinMatch.groupValues[1]
                                addPlayer(server.id, playerName)
                                
                                val notifyPlayers = true
                                if (notifyPlayers) {
                                    notificationHelper.showNotification(
                                        NotificationHelper.CHANNEL_PLAYERS,
                                        200 + playerName.hashCode(), 
                                        "Jogador Conectado",
                                        "$playerName entrou no servidor."
                                    )
                                }
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
                    Log.e("RealServerManager", "Error reading stdout", e)
                }
            }

            // Standard Error Consumer
            stderrScope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process.errorStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                         line?.let { logLine ->
                             Log.e("RealServerManager", "STDERR: $logLine")
                             emitLog(server.id, "[ERR] $logLine")
                         }
                    }
                } catch (e: Exception) {
                    Log.e("RealServerManager", "Error reading stderr", e)
                }
            }

            // Wait for process exit in a separate blocking scope to manage lifecycle
            // Wait for process exit in a separate blocking scope to manage lifecycle
            scope.launch {
                var shouldStop = true
                try {
                    // We simply wait for the process to exit naturally or via stop
                    withContext(Dispatchers.IO) {
                        try {
                            process.waitFor()
                        } catch (e: InterruptedException) {
                            // Ignored
                        }
                    }
                    
                    // Give streams a moment to flush
                    delay(200)
                    
                    val exitCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                         if (process.isAlive) "Alive (Timeout)" else process.exitValue().toString()
                    } else {
                        try { process.exitValue().toString() } catch(e: IllegalThreadStateException) { "Alive" }
                    }
                    
                    Log.i("RealServerManager", "Process exited. Final Exit Code: $exitCode")

                    // AUTO-RESTART STRATEGY FOR PAPERCLIP
                    // If exit code is 1 (common for Paperclip agent failure) AND we find a patched jar,
                    // it implies patching succeeded but the old process crashed. Restart immediately with the new jar.
                    if (exitCode == "1") {
                        val executionDir = File(context.filesDir, "server_execution_${server.id}")
                        val cacheDir = File(executionDir, "cache")
                        val patchedJar = cacheDir.listFiles { _, name -> 
                            name.startsWith("patched_") && name.endsWith(".jar") 
                        }?.firstOrNull()
                            
                        if (patchedJar != null && patchedJar.exists()) {
                                Log.i("RealServerManager", "Auto-Restart: Found patched JAR ${patchedJar.name}, restarting...")
                                emitLog(server.id, "Patch concluído! Reiniciando com JAR otimizado...")
                                
                                // Clean up OLD process from map so startServer doesn't bail out
                                // But DO NOT call stopServer() yet because it cleans up too much or might race
                                processes.remove(server.id) 
                                
                                startServer(server) // Recursive restart
                                shouldStop = false // Prevent finally block from running stopServer logic on the NEW process
                        }
                    }
                    
                    if (shouldStop) {
                        emitLog(server.id, "Processo finalizado (Exit Code: $exitCode)")
                    }
                    
                } catch (e: Exception) {
                    Log.e("RealServerManager", "Process monitor error", e)
                } finally {
                    if (shouldStop) {
                        stopServer(server.id)
                    }
                    // Always cancel the error stream reader of THIS process instance
                    stderrScope.cancel()
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

    private fun cleanupOrphanedProcesses(targetServerId: String? = null) {
        try {
            val executionDirs = context.filesDir.listFiles { _, name -> name.startsWith("server_execution_") } ?: return
            
            for (dir in executionDirs) {
                // If targeting a specific server, only check its dir
                if (targetServerId != null && !dir.name.endsWith(targetServerId)) continue
                
                val pidFile = File(dir, "server.pid")
                if (pidFile.exists()) {
                    val pid = pidFile.readText().trim().toLongOrNull()
                    if (pid != null) {
                        try {
                            // Check if process exists
                            val procDir = File("/proc/$pid")
                            if (procDir.exists()) {
                                // Check if it's ours (should be, we are non-root) and is a server
                                val cmdline = File(procDir, "cmdline").readText()
                                if (cmdline.contains("java") || cmdline.contains("php")) {
                                    // Make sure we aren't killing a process we are currently tracking!
                                    val isTracked = processes.values.any { getPid(it) == pid }
                                    if (!isTracked) {
                                        Log.w("RealServerManager", "Found orphan process $pid for ${dir.name}. Killing it.")
                                        android.os.Process.killProcess(pid.toInt())
                                        pidFile.delete()
                                    }
                                }
                            } else {
                                // Stale PID file
                                pidFile.delete()
                            }
                        } catch (e: Exception) {
                            Log.e("RealServerManager", "Failed to cleanup process $pid", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RealServerManager", "Error in cleanupOrphanedProcesses", e)
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
                    kotlinx.coroutines.delay(1000) // Update every 1 second as user requested
                    
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



    /**
     * Resolves a SAF URI to a real filesystem path if possible
     */
    private fun getRealPathFromSaf(uriStr: String): String? {
        try {
            val uri = Uri.parse(uriStr)
            val docId = if (uriStr.contains("/tree/")) {
                // Tree URI: content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2Fserver
                // We need the document ID part.
                if (uriStr.contains("/document/")) {
                    uriStr.substringAfter("/document/").replace("%3A", ":")
                } else {
                    uriStr.substringAfter("/tree/").replace("%3A", ":")
                }
            } else {
                uri.path?.substringAfterLast(":") ?: return null
            }

            if (uri.authority == "com.android.externalstorage.documents") {
                val split = docId.split(":")
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return "/storage/emulated/0/${split[1].replace("%2F", "/")}"
                }
            }
        } catch (e: Exception) {
            Log.e("RealServerManager", "Failed to resolve SAF real path", e)
        }
        return null
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

    private fun getMainClass(jarFile: File): String? {
        return try {
            JarInputStream(jarFile.inputStream()).use { jar ->
                val manifest = jar.manifest
                manifest?.mainAttributes?.getValue("Main-Class")
            }
        } catch (e: Exception) {
            Log.e("RealServerManager", "Failed to read Main-Class from JAR", e)
            null
        }
    }
}
