package com.lzofseven.mcserver.ui.screens.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.lzofseven.mcserver.data.local.entity.MCServerEntity
import com.lzofseven.mcserver.ui.screens.config.UiEvent.ShowToast
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import com.lzofseven.mcserver.util.SystemInfoUtils

@HiltViewModel
class ConfigViewModel @Inject constructor(
    private val repository: com.lzofseven.mcserver.data.repository.ServerRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {
    
    private val serverId: String = checkNotNull(savedStateHandle["serverId"])
    private var currentServer: com.lzofseven.mcserver.data.local.entity.MCServerEntity? = null

    private val _serverType = MutableStateFlow(ServerType.PAPER)
    val serverType: StateFlow<ServerType> = _serverType.asStateFlow()
    
    private val _mcVersion = MutableStateFlow("1.21")
    val mcVersion: StateFlow<String> = _mcVersion.asStateFlow()
    
    private val _ramAllocation = MutableStateFlow(1024)
    val ramAllocation: StateFlow<Int> = _ramAllocation.asStateFlow()
    
    private val _autoAcceptEula = MutableStateFlow(true)
    val autoAcceptEula: StateFlow<Boolean> = _autoAcceptEula.asStateFlow()
    
    private val _cpuCores = MutableStateFlow(2)
    val cpuCores: StateFlow<Int> = _cpuCores.asStateFlow()
    
    private val _forceMaxFrequency = MutableStateFlow(false)
    val forceMaxFrequency: StateFlow<Boolean> = _forceMaxFrequency.asStateFlow()
    
    private val _worldPath = MutableStateFlow("")
    val worldPath: StateFlow<String> = _worldPath.asStateFlow()

    // Configurações de Notificação
    private val _notifyStatus = MutableStateFlow(true)
    val notifyStatus: StateFlow<Boolean> = _notifyStatus.asStateFlow()

    private val _notifyPlayers = MutableStateFlow(true)
    val notifyPlayers: StateFlow<Boolean> = _notifyPlayers.asStateFlow()

    private val _notifyPerformance = MutableStateFlow(false)
    val notifyPerformance: StateFlow<Boolean> = _notifyPerformance.asStateFlow()

    private val _cpuThreshold = MutableStateFlow(80)
    val cpuThreshold: StateFlow<Int> = _cpuThreshold.asStateFlow()

    private val _ramThreshold = MutableStateFlow(90)
    val ramThreshold: StateFlow<Int> = _ramThreshold.asStateFlow()
    
    private val _javaVersion = MutableStateFlow(17)
    val javaVersion: StateFlow<Int> = _javaVersion.asStateFlow()
    
    private val _ramInfo = MutableStateFlow<SystemInfoUtils.RamInfo?>(null)
    val ramInfo: StateFlow<SystemInfoUtils.RamInfo?> = _ramInfo.asStateFlow()
    
    private val _uiEvent = kotlinx.coroutines.flow.MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()
    
    private val _restartRequiredEvent = kotlinx.coroutines.flow.MutableSharedFlow<Boolean>()
    val restartRequiredEvent = _restartRequiredEvent.asSharedFlow()
    
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    
    private val _saveSuccessEvent = MutableSharedFlow<Unit>()
    val saveSuccessEvent = _saveSuccessEvent.asSharedFlow()

    init {
        loadServerConfig()
        updateRamInfo()
    }

    fun updateRamInfo() {
        _ramInfo.value = SystemInfoUtils.getRamInfo(context)
    }

    private fun loadServerConfig() {
        viewModelScope.launch {
            val server = repository.getServerById(serverId)
            if (server != null) {
                currentServer = server
                _serverType.value = try { ServerType.valueOf(server.type) } catch (e: Exception) { ServerType.PAPER }
                _mcVersion.value = server.version
                _ramAllocation.value = server.ramAllocationMB
                _worldPath.value = server.path
                _javaVersion.value = server.javaVersion
                
                loadExtraConfig(server.uri ?: server.path, server.javaVersion)
            }
        }
    }

    fun selectServerType(type: ServerType) {
        _serverType.value = type
    }
    
    fun setRamAllocation(ram: Int) {
        _ramAllocation.value = ram
    }
    
    fun setAutoAcceptEula(accept: Boolean) {
        _autoAcceptEula.value = accept
    }
    
    fun setCpuCores(cores: Int) {
        _cpuCores.value = cores
    }
    
    fun setForceMaxFrequency(force: Boolean) {
        _forceMaxFrequency.value = force
    }
    
    fun setWorldPath(path: String) {
        _worldPath.value = path
    }

    fun setNotifyStatus(enabled: Boolean) {
        _notifyStatus.value = enabled
    }

    fun setNotifyPlayers(enabled: Boolean) {
        _notifyPlayers.value = enabled
    }

    fun setNotifyPerformance(enabled: Boolean) {
        _notifyPerformance.value = enabled
    }

    fun setCpuThreshold(threshold: Int) {
        _cpuThreshold.value = threshold
    }

    fun setRamThreshold(threshold: Int) {
        _ramThreshold.value = threshold
    }

    fun setJavaVersion(version: Int) {
        _javaVersion.value = version
    }

    fun saveConfig() {
        if (_isSaving.value) return
        _isSaving.value = true
        
        viewModelScope.launch {
            try {
            currentServer?.let { server ->
                val typeChanged = _serverType.value.name != server.type
                val ramChanged = _ramAllocation.value != server.ramAllocationMB
                
                // Get old properties to preserve level-name
                val propsManager = com.lzofseven.mcserver.util.ServerPropertiesManager(context, server.uri ?: server.path)
                val oldProps = propsManager.load()
                val currentLevelName = oldProps["level-name"] ?: "world"

                val updated = server.copy(
                    ramAllocationMB = _ramAllocation.value,
                    path = _worldPath.value,
                    uri = if (_worldPath.value.startsWith("content://")) _worldPath.value else server.uri,
                    type = _serverType.value.name,
                    javaVersion = _javaVersion.value
                )
                repository.updateServer(updated)
                
                // If engine changed, ensure we preserve the world folder name
                if (typeChanged) {
                    propsManager.save(mapOf("level-name" to currentLevelName))
                }
                
                // If type changed, delete old jar to force re-download
                if (typeChanged) {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            // Clean source directory jars
                            if (server.uri != null) {
                                val rootUri = android.net.Uri.parse(server.uri)
                                val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, rootUri)
                                rootDoc?.findFile("server.jar")?.delete()
                                rootDoc?.findFile("PocketMine-MP.phar")?.delete()
                            } else {
                                val serverDir = java.io.File(server.path)
                                java.io.File(serverDir, "server.jar").delete()
                                java.io.File(serverDir, "PocketMine-MP.phar").delete()
                            }
                            
                            // Clean execution directory (Safe Copy-to-Run area)
                            val executionDir = java.io.File(context.filesDir, "server_execution_${server.id}")
                            if (executionDir.exists()) {
                                executionDir.deleteRecursively()
                                executionDir.mkdirs()
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
                
                val props = java.util.Properties()
                props.setProperty("cpuCores", _cpuCores.value.toString())
                props.setProperty("forceMaxFrequency", _forceMaxFrequency.value.toString())
                props.setProperty("autoAcceptEula", _autoAcceptEula.value.toString())
                
                props.setProperty("javaVersion", _javaVersion.value.toString())
                
                // Notifications
                props.setProperty("notifyStatus", _notifyStatus.value.toString())
                props.setProperty("notifyPlayers", _notifyPlayers.value.toString())
                props.setProperty("notifyPerformance", _notifyPerformance.value.toString())
                props.setProperty("cpuThreshold", _cpuThreshold.value.toString())
                props.setProperty("ramThreshold", _ramThreshold.value.toString())
                
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        if (server.uri != null) {
                            val rootUri = android.net.Uri.parse(server.uri)
                            val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, rootUri)
                            if (rootDoc != null && rootDoc.exists()) {
                                // Save manager_config.properties
                                val configFile = rootDoc.findFile("manager_config.properties") 
                                    ?: rootDoc.createFile("text/plain", "manager_config.properties")
                                configFile?.let {
                                    context.contentResolver.openOutputStream(it.uri, "wt")?.use { out ->
                                        props.store(out, "MC Server Manager Extra Config")
                                    }
                                }
                                
                                // Save EULA
                                if (_autoAcceptEula.value) {
                                    val eulaFile = rootDoc.findFile("eula.txt") 
                                        ?: rootDoc.createFile("text/plain", "eula.txt")
                                    eulaFile?.let {
                                        context.contentResolver.openOutputStream(it.uri, "wt")?.use { out ->
                                            out.write("eula=true\n".toByteArray())
                                        }
                                    }
                                }
                            }
                        } else {
                            // File Logic
                            val serverDir = java.io.File(server.path)
                            if (!serverDir.exists()) serverDir.mkdirs()
                            
                            val configFile = java.io.File(serverDir, "manager_config.properties")
                            configFile.outputStream().use { 
                                props.store(it, "MC Server Manager Extra Config") 
                            }
                            
                            if (_autoAcceptEula.value) {
                                val eulaFile = java.io.File(serverDir, "eula.txt")
                                eulaFile.writeText("eula=true\n")
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                _uiEvent.emit(UiEvent.ShowToast("Configurações salvas com sucesso!"))
                
                // Check if engine or critical resource changed
                if (typeChanged || ramChanged) {
                    _restartRequiredEvent.emit(true)
                }
                _saveSuccessEvent.emit(Unit)
            }
        } finally {
            _isSaving.value = false
        }
    }
}

    fun deleteServer(navController: androidx.navigation.NavController) {
        viewModelScope.launch {
            currentServer?.let { server ->
                repository.deleteServer(server)
                _uiEvent.emit(UiEvent.ShowToast("Servidor removido"))
                navController.navigate("server_list") {
                    popUpTo("server_list") { inclusive = true }
                }
            }
        }
    }
    
    private fun loadExtraConfig(serverPath: String, defaultJavaVersion: Int = 17) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val props = java.util.Properties()
            var loaded = false
            
            // Try SAF first if path is Content URI
            if (serverPath.startsWith("content://")) {
                try {
                     val uri = android.net.Uri.parse(serverPath)
                     val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                     var configFile = rootDoc?.findFile("manager_config.properties")
                     if (configFile == null) {
                         configFile = rootDoc?.findFile("manager_config.properties.txt")
                     }
                     
                     if (configFile != null) {
                         context.contentResolver.openInputStream(configFile.uri)?.use { props.load(it) }
                         loaded = true
                         android.util.Log.d("ConfigViewModel", "LOAD_CONFIG: Loaded from SAF $serverPath")
                     }
                     
                     // Check EULA for defaults logic
                     if (!loaded) {
                         val eulaFile = rootDoc?.findFile("eula.txt")
                         if (eulaFile != null) {
                             val content = context.contentResolver.openInputStream(eulaFile.uri)?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                             if (content.contains("eula=true")) {
                                 _autoAcceptEula.value = true
                             }
                         }
                     }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (!loaded) {
                // Try File Logic
                val configFile = java.io.File(serverPath, "manager_config.properties")
                if (configFile.exists()) {
                    try {
                        configFile.inputStream().use { props.load(it) }
                        loaded = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                     val eulaFile = java.io.File(serverPath, "eula.txt")
                     if (eulaFile.exists() && eulaFile.readText().contains("eula=true")) {
                         _autoAcceptEula.value = true
                     }
                }
            }
            
            if (loaded) {
                try {
                    val cores = props.getProperty("cpuCores", "2").toIntOrNull() ?: 2
                    val maxFreq = props.getProperty("forceMaxFrequency", "false").toBoolean()
                    val autoEula = props.getProperty("autoAcceptEula", "true").toBoolean()
                    
                     _cpuCores.value = cores
                    _forceMaxFrequency.value = maxFreq
                    _autoAcceptEula.value = autoEula
                    
                    android.util.Log.d("ConfigViewModel", "LOAD_CONFIG: cpuCores=$cores, maxFreq=$maxFreq, autoEula=$autoEula")

                    _notifyStatus.value = props.getProperty("notifyStatus", "true").toBoolean()
                    _notifyPlayers.value = props.getProperty("notifyPlayers", "true").toBoolean()
                    _notifyPerformance.value = props.getProperty("notifyPerformance", "false").toBoolean()
                    _cpuThreshold.value = props.getProperty("cpuThreshold", "80").toIntOrNull() ?: 80
                    _ramThreshold.value = props.getProperty("ramThreshold", "90").toIntOrNull() ?: 90
                } catch (e: Exception) { e.printStackTrace() }
            } else {
                android.util.Log.d("ConfigViewModel", "LOAD_CONFIG: No config file found at $serverPath, using defaults")
            }
        }
    }
}

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
}
