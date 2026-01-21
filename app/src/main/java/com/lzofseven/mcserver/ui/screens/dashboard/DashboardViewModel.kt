package com.lzofseven.mcserver.ui.screens.dashboard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lzofseven.mcserver.core.execution.RealServerManager
import com.lzofseven.mcserver.core.execution.ServerStatus
import com.lzofseven.mcserver.data.local.entity.MCServerEntity
import com.lzofseven.mcserver.data.repository.ServerRepository
import com.lzofseven.mcserver.util.NotificationHelper
import com.lzofseven.mcserver.util.ServerInstaller
import com.lzofseven.mcserver.util.ServerPropertiesManager
import com.lzofseven.mcserver.util.McVersionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import com.lzofseven.mcserver.core.execution.PlayitManager
import okhttp3.OkHttpClient
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lzofseven.mcserver.core.java.JavaVersionManager
import com.lzofseven.mcserver.workers.StartupWorker
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val notificationHelper: NotificationHelper,
    private val serverManager: RealServerManager,
    private val playitManager: PlayitManager,
    private val installer: ServerInstaller,
    private val workManager: WorkManager,
    private val javaVersionManager: JavaVersionManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverId: String = checkNotNull(savedStateHandle["serverId"])

    private val _serverEntity = MutableStateFlow<MCServerEntity?>(null)
    val serverEntity: StateFlow<MCServerEntity?> = _serverEntity.asStateFlow()
    
    private val _serverStatus = MutableStateFlow(ServerStatus.STOPPED)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()
    
    private val _playitLink = MutableStateFlow<String?>(null)
    val playitLink: StateFlow<String?> = _playitLink.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Int?>(null)
    val downloadProgress: StateFlow<Int?> = _downloadProgress.asStateFlow()

    private val _navigateToConsole = MutableSharedFlow<Unit>()
    val navigateToConsole = _navigateToConsole.asSharedFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    private var isToggling = false

    private val _showEulaDialog = MutableStateFlow(false)
    val showEulaDialog: StateFlow<Boolean> = _showEulaDialog.asStateFlow()

    private val _showRamDialog = MutableStateFlow(false)
    val showRamDialog: StateFlow<Boolean> = _showRamDialog.asStateFlow()
    
    fun openRamDialog() { _showRamDialog.value = true }
    fun closeRamDialog() { _showRamDialog.value = false }
    
    fun updateRamAllocation(mb: Int) {
        viewModelScope.launch {
            val server = _serverEntity.value ?: return@launch
            val updatedServer = server.copy(ramAllocationMB = mb)
            repository.updateServer(updatedServer)
            _serverEntity.value = updatedServer
            log("RAM allocation updated to ${mb}MB")
            _showRamDialog.value = false
        }
    }
    
    // Real Console Flow (Last 10 lines)
    val consoleLogs: StateFlow<List<String>> = serverManager.getConsoleFlow(serverId)
        .scan(emptyList<String>()) { acc, value ->
            (acc + value).takeLast(10)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // Players Flow
    val onlinePlayers = serverManager.getPlayerListFlow(serverId)
    
    // Real Stats Flow
    val serverStats = serverManager.getServerStatsFlow(serverId)
    
    val playitStatus = playitManager.status
    val playitClaimLink = playitManager.claimLink
    
    private var propertiesManager: ServerPropertiesManager? = null
    // removed private var installer manual instance

    init {
        loadServer()
    }

    private fun loadServer() {
        viewModelScope.launch {
            val server = repository.getServerById(serverId)
            _serverEntity.value = server
            if (server != null) {
                initializeManagers(server)
            }
        }
    }

    private fun initializeManagers(server: MCServerEntity) {
        propertiesManager = ServerPropertiesManager(context, server.uri ?: server.path)
        
        loadProperties()
        checkEula()
        checkServerPersistence(server)
    }

    private fun checkServerPersistence(server: MCServerEntity) {
        viewModelScope.launch {
            if (serverManager.isRunning(server.id)) {
                _serverStatus.value = ServerStatus.RUNNING
                log("Servidor detectado em execução (persistência)")
            } else {
                // Try to detect externally
                val isActuallyRunning = serverManager.checkProcessHealth(server.id)
                if (isActuallyRunning) {
                    _serverStatus.value = ServerStatus.RUNNING
                    log("Servidor detectado via processo do sistema")
                }
            }
        }
    }

    private fun checkEula() {
        val server = _serverEntity.value ?: return
        try {
            if (!installer.isEulaAccepted(server.uri ?: server.path)) {
                _showEulaDialog.value = true
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Error checking EULA", e)
        }
    }

    fun acceptEula() {
        val server = _serverEntity.value ?: return
        val targetPath = server.uri ?: server.path
        if (targetPath.isEmpty()) {
            log("Server has no path or URI, cannot accept EULA.")
            android.widget.Toast.makeText(context, "Erro: Servidor sem caminho definido!", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        viewModelScope.launch {
            val success = installer.acceptEula(targetPath)
            if (success) {
                _showEulaDialog.value = false
                log("EULA accepted successfully")
            } else {
                log("Failed to accept EULA at $targetPath")
                android.widget.Toast.makeText(context, "Erro ao gravar EULA. Verifique permissões da pasta.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun updateServerUri(uri: String, path: String) {
        viewModelScope.launch {
            val server = _serverEntity.value ?: return@launch
            val updatedServer = server.copy(uri = uri, path = path)
            repository.updateServer(updatedServer)
            _serverEntity.value = updatedServer
            initializeManagers(updatedServer)
            log("Server folder linked: $path")
            android.widget.Toast.makeText(context, "Pasta vinculada com sucesso!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    // Properties Flow
    private val _properties = MutableStateFlow<Map<String, String>>(emptyMap())
    val properties: StateFlow<Map<String, String>> = _properties.asStateFlow()

    fun loadProperties() {
        viewModelScope.launch {
            _properties.value = propertiesManager?.load() ?: emptyMap()
        }
    }

    fun updateProperty(key: String, value: String) {
        viewModelScope.launch {
            propertiesManager?.save(mapOf(key to value))
            loadProperties()
        }
    }

    private fun log(message: String) {
        android.util.Log.d("DashboardViewModel", message)
        serverManager.logToConsole(serverId, "[System] $message")
    }

    fun toggleServer() {
        if (isToggling) return
        isToggling = true
        
        log("Iniciando acao de alternancia do servidor")
        viewModelScope.launch {
            try {
                val server = _serverEntity.value ?: return@launch
                log("Servidor encontrado: ${server.name}")
                
                val checkPath = server.uri ?: server.path
                
                // Check for MANAGE_EXTERNAL_STORAGE on Android 11+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    if (!android.os.Environment.isExternalStorageManager()) {
                        log("Acesso total aos arquivos nao concedido")
                        _showPermissionDialog.value = true
                        return@launch
                    }
                }

                if (!installer.isEulaAccepted(checkPath)) {
                    log("EULA nao aceita")
                    _showEulaDialog.value = true
                    return@launch
                }

            // check and download server.jar if missing
            if (!downloadServerJarIfNeeded(server)) {
                return@launch
            }

            // Java Check
            val requiredJava = McVersionUtils.getRequiredJavaVersion(server.version)
            val isInstalled = javaVersionManager.isJavaInstalled(requiredJava)
            log("Java $requiredJava installed: $isInstalled")
            
            if (!isInstalled) {
                log("Starting Java installation")
                startJavaInstallation(requiredJava)
                return@launch
            }
            
            if (serverManager.isRunning(serverId)) {
                log("Parando servidor...")
                stopServer()
                playitManager.stop()
            } else {
                log("Iniciando servidor...")
                _navigateToConsole.emit(Unit)
                startServer(server)
                playitManager.start()
            }
        } finally {
            isToggling = false
        }
    }
}

    private fun startJavaInstallation(version: Int) {
        val workRequest = OneTimeWorkRequestBuilder<StartupWorker>()
            .setInputData(workDataOf(StartupWorker.KEY_JAVA_VERSION to version))
            .build()
        
        workManager.enqueue(workRequest)
        _serverStatus.value = ServerStatus.INSTALLING
        
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workRequest.id).collect { workInfo ->
                if (workInfo != null) {
                    when(workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            _serverStatus.value = ServerStatus.STOPPED
                            _downloadProgress.value = null // Reset progress
                            notificationHelper.showNotification(
                                NotificationHelper.CHANNEL_STATUS, 
                                201, 
                                "Instalação Concluída", 
                                "Java $version instalado com sucesso. Você já pode iniciar o servidor."
                            )
                        }
                        WorkInfo.State.FAILED -> {
                            _serverStatus.value = ServerStatus.STOPPED
                            _downloadProgress.value = null // Reset progress
                            val error = workInfo.outputData.getString("error") ?: "Worker FAILED (State: ${workInfo.state}, RunAttempt: ${workInfo.runAttemptCount})"
                            log("Installation failed: $error")
                             notificationHelper.showNotification(
                                NotificationHelper.CHANNEL_STATUS, 
                                201, 
                                "Erro na Instalação", 
                                error
                            )
                        }
                        WorkInfo.State.RUNNING -> {
                             _serverStatus.value = ServerStatus.INSTALLING
                             // Optional: Update downloadProgress map if passed via progress
                             val progress = workInfo.progress.getInt("progress", 0) // Need to emit progress in Worker
                             if (progress > 0) _downloadProgress.value = progress
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private suspend fun startServer(server: MCServerEntity) {
        _serverStatus.value = ServerStatus.STARTING

        serverManager.startServer(server)
        
        _serverStatus.value = ServerStatus.RUNNING
        
        // Single notification when server is actually online
        notificationHelper.showNotification(
            NotificationHelper.CHANNEL_STATUS,
            100,
            "Servidor Online",
            "${server.name} iniciado com ${server.ramAllocationMB}MB RAM."
        )
    }
    
    private suspend fun stopServer() {
        _serverStatus.value = ServerStatus.STOPPING
        serverManager.stopServer(serverId)
        _serverStatus.value = ServerStatus.STOPPED
        
        notificationHelper.showNotification(
            NotificationHelper.CHANNEL_STATUS,
            101,
            "Servidor Parado",
            "Comando de parada enviado."
        )
    }

    private suspend fun downloadServerJarIfNeeded(server: MCServerEntity): Boolean {
        val serverDir = File(server.path)
        val isPocketMine = server.type.equals("POCKETMINE", ignoreCase = true)
        val isBedrock = server.type.equals("BEDROCK", ignoreCase = true)
        
        val targetFilename = if (isPocketMine) "PocketMine-MP.phar" else "server.jar"
        val serverJar = File(serverDir, targetFilename)
        
        // 1. Download Base Server if missing
        if (!serverJar.exists()) {
            log("$targetFilename not found, attempting auto-download for version ${server.version}")
            
            val downloadUrl = try {
                McVersionUtils.getDownloadUrl(server.type, server.version)
            } catch (e: Exception) {
                log("Error resolving version URL: ${e.message}")
                return false
            }
                
            try {
                _serverStatus.value = ServerStatus.INSTALLING
                val targetPath = server.uri ?: server.path
                installer.downloadServer(downloadUrl, targetPath, targetFilename).collect { status ->
                    when (status) {
                        is com.lzofseven.mcserver.util.DownloadStatus.Progress -> {
                            _downloadProgress.value = status.percentage
                        }
                        is com.lzofseven.mcserver.util.DownloadStatus.Finished -> {
                            log("$targetFilename downloaded successfully")
                            _downloadProgress.value = null
                        }
                        is com.lzofseven.mcserver.util.DownloadStatus.Error -> {
                            log("Error downloading $targetFilename: ${status.message}")
                            _downloadProgress.value = null
                            throw Exception(status.message)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                _serverStatus.value = ServerStatus.STOPPED
                log("Auto-download failed: ${e.message}")
                return false
            }
        }
        
        // 2. Download Geyser Plugin if Bedrock type
        if (isBedrock) {
            val pluginsDir = File(serverDir, "plugins")
            if (!pluginsDir.exists()) pluginsDir.mkdirs()
            
            val geyserJar = File(pluginsDir, "Geyser-Spigot.jar")
            if (!geyserJar.exists()) {
                log("Geyser plugin not found, downloading...")
                try {
                     _serverStatus.value = ServerStatus.INSTALLING
                     val geyserUrl = McVersionUtils.getGeyserUrl()
                     // We can't easily use 'installer' for subfolders unless we create a specialized method or pass full path 
                     // if installer accepts absolute path or relative.
                     // Assuming installer.downloadServer takes a 'base path' (uri/path) and 'filename'. 
                     // If it uses SAF, we might be limited to root.
                     // However, 'installer' implementation (ServerInstaller) likely handles DocumentFile.
                     
                     // Workaround: Use simple OkHttp download if path is local file, or try to use installer if it supports subdirs.
                     // Given current architecture, let's assume local file access for simplicity since we have updated RealServerManager with File().
                     
                     if (server.uri == null) {
                         // Direct File Access
                         val request = okhttp3.Request.Builder().url(geyserUrl).build()
                         val response = OkHttpClient().newCall(request).execute()
                         if (!response.isSuccessful) throw Exception("Failed to download Geyser")
                         
                         val sink = geyserJar.sink().buffer()
                         sink.writeAll(response.body!!.source())
                         sink.close()
                         log("Geyser downloaded to ${geyserJar.absolutePath}")
                     } else {
                         // SAF - This is harder without extending Installer. 
                         // For now, skip or warn. Or assume 'plugins' folder doesn't exist in SAF root view easily.
                         // But we can try downloading to "Geyser-Spigot.jar" in ROOT and asking user to move it? No that's bad.
                         // Let's just log a warning for SAF users or implement basic download.
                         log("SAF Mode: Auto-download of Geyser into /plugins/ not fully supported yet. Please install Geyser manually.")
                     }
                     
                } catch (e: Exception) {
                    log("Failed to download Geyser: ${e.message}")
                    // Don't block server start, just warn
                } finally {
                     _serverStatus.value = ServerStatus.STOPPED
                     _downloadProgress.value = null
                }
            }
        }

        return true
    }


    fun dismissPermissionDialog() {
        _showPermissionDialog.value = false
    }

    fun openPermissionSettings() {
        _showPermissionDialog.value = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = android.net.Uri.parse("package:${context.packageName}")
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
