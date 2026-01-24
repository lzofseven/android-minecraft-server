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
    private val globalSettingsManager: com.lzofseven.mcserver.util.GlobalSettingsManager,
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
    
    private val _ramInfo = MutableStateFlow<com.lzofseven.mcserver.util.SystemInfoUtils.RamInfo?>(null)
    val ramInfo: StateFlow<com.lzofseven.mcserver.util.SystemInfoUtils.RamInfo?> = _ramInfo.asStateFlow()
    
    private val _serverIconPath = MutableStateFlow<String?>(null)
    val serverIconPath: StateFlow<String?> = _serverIconPath.asStateFlow()

    private val _serverIconUpdate = MutableStateFlow(0L)
    val serverIconUpdate: StateFlow<Long> = _serverIconUpdate.asStateFlow()
    
    val notificationsEnabled = globalSettingsManager.notificationsEnabled

    fun setNotificationsEnabled(enabled: Boolean) {
        globalSettingsManager.setNotificationsEnabled(enabled)
    }
    
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
    
    val statsHistory: StateFlow<List<com.lzofseven.mcserver.core.execution.RealServerManager.ServerStats>> = serverStats
        .scan(emptyList<com.lzofseven.mcserver.core.execution.RealServerManager.ServerStats>()) { acc, value ->
            (acc + value).takeLast(30)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val playitStatus = playitManager.status
    val playitClaimLink = playitManager.claimLink
    val playitAddress = playitManager.address
    
    private var propertiesManager: ServerPropertiesManager? = null
    // removed private var installer manual instance

    init {
        loadServer()
        updateRamInfo()
    }

    private fun updateRamInfo() {
        viewModelScope.launch {
            val info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.lzofseven.mcserver.util.SystemInfoUtils.getRamInfo(context)
            }
            _ramInfo.value = info
        }
    }

    private fun loadServer() {
        viewModelScope.launch {
            repository.getServerByIdFlow(serverId)
                .distinctUntilChanged()
                .collect { server ->
                    val oldServer = _serverEntity.value
                    _serverEntity.value = server
                    
                    if (server != null) {
                        // Only re-init if path/uri or java version changed
                        if (oldServer == null || 
                            oldServer.uri != server.uri || 
                            oldServer.path != server.path ||
                            oldServer.javaVersion != server.javaVersion ||
                            oldServer.ramAllocationMB != server.ramAllocationMB) {
                            initializeManagers(server)
                        }
                    }
                }
        }
    }

    private suspend fun initializeManagers(server: MCServerEntity) {
        propertiesManager = ServerPropertiesManager(context, server.uri ?: server.path)
        
        refreshIcon(server)

        loadProperties()
        checkEula()
        checkServerPersistence(server)
    }

    fun refreshIcon(server: MCServerEntity? = _serverEntity.value) {
        server ?: return
        viewModelScope.launch {
            val iconPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val executionDir = File(context.filesDir, "server_execution_${server.id}")
                val execIcon = File(executionDir, "server-icon.png")
                
                // Priority 1: Active Execution Directory (most recent)
                if (execIcon.exists()) {
                    return@withContext execIcon.absolutePath
                }
                
                // Priority 2: Source Directory
                val path = server.uri ?: server.path
                if (path.startsWith("content://")) {
                    val uri = android.net.Uri.parse(path)
                    val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                    val iconDoc = rootDoc?.findFile("server-icon.png")
                    iconDoc?.uri?.toString()
                } else {
                    val iconFile = File(server.path, "server-icon.png")
                    if (iconFile.exists()) {
                        iconFile.absolutePath
                    } else {
                        null
                    }
                }
            }
            _serverIconPath.value = iconPath
            _serverIconUpdate.value = System.currentTimeMillis()
        }
    }

    private fun checkServerPersistence(server: MCServerEntity) {
        viewModelScope.launch {
            if (serverManager.isRunning(server.id)) {
                _serverStatus.value = ServerStatus.RUNNING
                log("Servidor detectado em execução (persistência)")
                playitManager.start()
            } else {
                // Try to detect externally
                val isActuallyRunning = serverManager.checkProcessHealth(server.id)
                if (isActuallyRunning) {
                    _serverStatus.value = ServerStatus.RUNNING
                    log("Servidor detectado via processo do sistema")
                    playitManager.start()
                }
            }
        }
    }

    private suspend fun checkEula() {
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

    val isWhitelistEnabled: StateFlow<Boolean> = _properties.map {
        it["white-list"]?.toBoolean() ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleWhitelist(enabled: Boolean) {
        viewModelScope.launch {
            // 1. Send Command (Runtime)
            val cmd = if (enabled) "whitelist on" else "whitelist off"
            serverManager.sendCommand(serverId, cmd)
            
            // 2. Update Persisted Property
            propertiesManager?.save(mapOf("white-list" to enabled.toString()))
            
            // 3. Refresh Local State
            loadProperties()
            
            log("Whitelist set to $enabled")
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

                // 1. Check if server is already running (Priority Stop)
                if (serverManager.isRunning(serverId)) {
                    log("Parando servidor...")
                    stopServer()
                    playitManager.stop()
                    return@launch
                }

                // 2. Start Request Checks
                
                // Check EULA
                if (!installer.isEulaAccepted(checkPath)) {
                    log("EULA nao aceita")
                    _showEulaDialog.value = true
                    return@launch
                }

                // Check/Download Server JAR
                if (!downloadServerJarIfNeeded(server)) {
                    return@launch
                }

                // Java Check
                val userSelectedJava = server.javaVersion
                val recommendedJava = McVersionUtils.getRequiredJavaVersion(server.version)
                val javaToUse = if (userSelectedJava > 0) userSelectedJava else recommendedJava
                
                val isInstalled = javaVersionManager.isJavaInstalled(javaToUse)
                log("Java check: userSelected=$userSelectedJava, recommended=$recommendedJava, using=$javaToUse, installed=$isInstalled")
                
                if (!isInstalled) {
                    log("Starting Java installation for version $javaToUse")
                    startJavaInstallation(javaToUse)
                    return@launch
                }
                
                log("Iniciando servidor...")
                _navigateToConsole.emit(Unit)
                startServer(server)
                playitManager.start()

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
        val notifyStatus = propertiesManager?.load()?.get("notifyStatus")?.toBoolean() ?: true
        if (notifyStatus) {
            notificationHelper.showNotification(
                NotificationHelper.CHANNEL_STATUS,
                100,
                "Servidor Online",
                "${server.name} iniciado com ${server.ramAllocationMB}MB RAM."
            )
        }
    }
    
    private suspend fun stopServer() {
        _serverStatus.value = ServerStatus.STOPPING
        serverManager.stopServer(serverId)
        _serverStatus.value = ServerStatus.STOPPED
        
        val notifyStatus = propertiesManager?.load()?.get("notifyStatus")?.toBoolean() ?: true
        if (notifyStatus) {
            notificationHelper.showNotification(
                NotificationHelper.CHANNEL_STATUS,
                101,
                "Servidor Parado",
                "Comando de parada enviado."
            )
        }
    }

    private suspend fun downloadServerJarIfNeeded(server: MCServerEntity): Boolean {
        val serverDir = File(server.path)
        val targetFilename = "server.jar"
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
