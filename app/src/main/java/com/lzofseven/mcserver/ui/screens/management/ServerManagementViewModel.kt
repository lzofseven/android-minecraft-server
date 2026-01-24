package com.lzofseven.mcserver.ui.screens.management

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lzofseven.mcserver.data.repository.ServerRepository
import com.lzofseven.mcserver.util.ServerPropertiesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.lzofseven.mcserver.ui.screens.config.UiEvent

@HiltViewModel
class ServerManagementViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val serverManager: com.lzofseven.mcserver.core.execution.RealServerManager,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverId: String = checkNotNull(savedStateHandle["serverId"])
    
    private val _motd = MutableStateFlow("")
    val motd: StateFlow<String> = _motd.asStateFlow()

    private val _maxPlayers = MutableStateFlow("20")
    val maxPlayers: StateFlow<String> = _maxPlayers.asStateFlow()

    private val _onlineMode = MutableStateFlow(false)
    val onlineMode: StateFlow<Boolean> = _onlineMode.asStateFlow()

    private val _gameMode = MutableStateFlow("survival")
    val gameMode: StateFlow<String> = _gameMode.asStateFlow()

    // Rede & Identificação
    private val _serverPort = MutableStateFlow("25565")
    val serverPort: StateFlow<String> = _serverPort.asStateFlow()
    private val _serverIp = MutableStateFlow("")
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()
    private val _enableQuery = MutableStateFlow(false)
    val enableQuery: StateFlow<Boolean> = _enableQuery.asStateFlow()
    private val _enableRcon = MutableStateFlow(false)
    val enableRcon: StateFlow<Boolean> = _enableRcon.asStateFlow()
    private val _rconPassword = MutableStateFlow("")
    val rconPassword: StateFlow<String> = _rconPassword.asStateFlow()

    // Mundo
    private val _levelName = MutableStateFlow("world")
    val levelName: StateFlow<String> = _levelName.asStateFlow()
    private val _levelSeed = MutableStateFlow("")
    val levelSeed: StateFlow<String> = _levelSeed.asStateFlow()
    private val _levelType = MutableStateFlow("default")
    val levelType: StateFlow<String> = _levelType.asStateFlow()
    private val _pvp = MutableStateFlow(true)
    val pvp: StateFlow<Boolean> = _pvp.asStateFlow()
    private val _difficulty = MutableStateFlow("normal")
    val difficulty: StateFlow<String> = _difficulty.asStateFlow()
    private val _hardcore = MutableStateFlow(false)
    val hardcore: StateFlow<Boolean> = _hardcore.asStateFlow()
    private val _allowNether = MutableStateFlow(true)
    val allowNether: StateFlow<Boolean> = _allowNether.asStateFlow()
    private val _generateStructures = MutableStateFlow(true)
    val generateStructures: StateFlow<Boolean> = _generateStructures.asStateFlow()
    private val _allowFlight = MutableStateFlow(false)
    val allowFlight: StateFlow<Boolean> = _allowFlight.asStateFlow()
    private val _spawnAnimals = MutableStateFlow(true)
    val spawnAnimals: StateFlow<Boolean> = _spawnAnimals.asStateFlow()
    private val _spawnNpcs = MutableStateFlow(true)
    val spawnNpcs: StateFlow<Boolean> = _spawnNpcs.asStateFlow()

    // Performance
    private val _viewDistance = MutableStateFlow("10")
    val viewDistance: StateFlow<String> = _viewDistance.asStateFlow()
    private val _simulationDistance = MutableStateFlow("10")
    val simulationDistance: StateFlow<String> = _simulationDistance.asStateFlow()

    private val _whiteList = MutableStateFlow(false)
    val whiteList: StateFlow<Boolean> = _whiteList.asStateFlow()

    private val _javaVersion = MutableStateFlow(17)
    val javaVersion: StateFlow<Int> = _javaVersion.asStateFlow()

    private val _autoStart = MutableStateFlow(false)
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()
    
    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()
    
    val serverStatus: StateFlow<com.lzofseven.mcserver.core.execution.ServerStatus> = 
        serverManager.getServerStatusFlow(serverId)

    fun toggleServer() {
        viewModelScope.launch {
            if (serverManager.isRunning(serverId)) {
                serverManager.stopServer(serverId)
            } else {
                val server = repository.getServerById(serverId)
                if (server != null) {
                    serverManager.startServer(server)
                }
            }
        }
    }
    
    private var propertiesManager: ServerPropertiesManager? = null

    init {
        loadProperties()
        viewModelScope.launch {
            val server = repository.getServerById(serverId)
            if (server != null) {
                val path = server.uri ?: server.path
                if (path.startsWith("content://")) {
                     val uri = android.net.Uri.parse(path)
                     val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                     val iconDoc = rootDoc?.findFile("server-icon.png")
                     _serverIconPath.value = iconDoc?.uri?.toString() ?: File(server.path, "server-icon.png").absolutePath
                } else {
                    _serverIconPath.value = File(server.path, "server-icon.png").absolutePath
                }
            }
        }
    }

    private fun loadProperties() {
        viewModelScope.launch {
            try {
                val server = repository.getServerById(serverId) ?: return@launch
                log("Loading properties for server: ${server.name} at ${server.path}")
                propertiesManager = ServerPropertiesManager(context, server.uri ?: server.path)
                
                val props = propertiesManager?.load() ?: emptyMap()
                log("Properties loaded: ${props.size} items")
                
                _motd.value = (props["motd"] ?: "A Minecraft Server").replace('§', '&')
                _maxPlayers.value = props["max-players"] ?: "20"
                _onlineMode.value = props["online-mode"]?.toBoolean() ?: false // Default to false (cracked/pirata) is safer for local mobile servers
                _gameMode.value = props["gamemode"] ?: "survival"
                
                // Load DB specific settings
                _javaVersion.value = server.javaVersion
                _autoStart.value = server.autoStart

                _serverPort.value = props["server-port"] ?: "25565"
                _serverIp.value = props["server-ip"] ?: ""
                _enableQuery.value = props["enable-query"]?.toBoolean() ?: false
                _enableRcon.value = props["enable-rcon"]?.toBoolean() ?: false
                _rconPassword.value = props["rcon.password"] ?: ""

                _levelName.value = props["level-name"] ?: "world"
                _levelSeed.value = props["level-seed"] ?: ""
                _levelType.value = props["level-type"] ?: "default"
                _pvp.value = props["pvp"]?.toBoolean() ?: true
                _difficulty.value = props["difficulty"] ?: "normal"
                _hardcore.value = props["hardcore"]?.toBoolean() ?: false
                _allowNether.value = props["allow-nether"]?.toBoolean() ?: true
                _generateStructures.value = props["generate-structures"]?.toBoolean() ?: true
                _allowFlight.value = props["allow-flight"]?.toBoolean() ?: false
                _spawnAnimals.value = props["spawn-animals"]?.toBoolean() ?: true
                _spawnNpcs.value = props["spawn-npcs"]?.toBoolean() ?: true

                _viewDistance.value = props["view-distance"] ?: "10"
                _simulationDistance.value = props["simulation-distance"] ?: "10"
                _whiteList.value = props["white-list"]?.toBoolean() ?: false
            } catch (e: Exception) {
                log("Error loading properties: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun log(message: String) {
        android.util.Log.d("ServerMgmtVM", message)
    }

    fun setMotd(value: String) { _motd.value = value }
    fun setMaxPlayers(value: String) { _maxPlayers.value = value }
    fun setOnlineMode(value: Boolean) { _onlineMode.value = value }
    fun setGameMode(value: String) { _gameMode.value = value }

    fun setServerPort(value: String) { _serverPort.value = value }
    fun setServerIp(value: String) { _serverIp.value = value }
    fun setEnableQuery(value: Boolean) { _enableQuery.value = value }
    fun setEnableRcon(value: Boolean) { _enableRcon.value = value }
    fun setRconPassword(value: String) { _rconPassword.value = value }

    fun setLevelName(value: String) { _levelName.value = value }
    fun setLevelSeed(value: String) { _levelSeed.value = value }
    fun setLevelType(value: String) { _levelType.value = value }
    fun setPvp(value: Boolean) { _pvp.value = value }
    fun setDifficulty(value: String) { _difficulty.value = value }
    fun setHardcore(value: Boolean) { _hardcore.value = value }
    fun setAllowNether(value: Boolean) { _allowNether.value = value }
    fun setGenerateStructures(value: Boolean) { _generateStructures.value = value }
    fun setAllowFlight(value: Boolean) { _allowFlight.value = value }
    fun setSpawnAnimals(value: Boolean) { _spawnAnimals.value = value }
    fun setSpawnNpcs(value: Boolean) { _spawnNpcs.value = value }

    fun setViewDistance(value: String) { _viewDistance.value = value }
    fun setSimulationDistance(value: String) { _simulationDistance.value = value }
    fun setWhiteList(value: Boolean) { _whiteList.value = value }
    fun setJavaVersion(value: Int) { _javaVersion.value = value }
    fun setAutoStart(value: Boolean) { _autoStart.value = value }

    fun saveProperties() {
        viewModelScope.launch {
            propertiesManager?.save(mapOf(
                "motd" to _motd.value.replace('&', '§'),
                "max-players" to _maxPlayers.value,
                "online-mode" to _onlineMode.value.toString(),
                "gamemode" to _gameMode.value,
                "server-port" to _serverPort.value,
                "server-ip" to _serverIp.value,
                "enable-query" to _enableQuery.value.toString(),
                "enable-rcon" to _enableRcon.value.toString(),
                "rcon.password" to _rconPassword.value,
                "level-name" to _levelName.value,
                "level-seed" to _levelSeed.value,
                "level-type" to _levelType.value,
                "pvp" to _pvp.value.toString(),
                "difficulty" to _difficulty.value,
                "hardcore" to _hardcore.value.toString(),
                "allow-nether" to _allowNether.value.toString(),
                "generate-structures" to _generateStructures.value.toString(),
                "allow-flight" to _allowFlight.value.toString(),
                "spawn-animals" to _spawnAnimals.value.toString(),
                "spawn-npcs" to _spawnNpcs.value.toString(),
                "view-distance" to _viewDistance.value,
                "simulation-distance" to _simulationDistance.value,
                "white-list" to _whiteList.value.toString()
            ))

            // CRITICAL FIX: If server is running, we MUST also update the execution directory 
            // otherwise the sync on shutdown will overwrite our choices with the old ones.
            val server = repository.getServerById(serverId)
            if (server != null) {
                // Update DB entry specifically for Java and AutoStart
                repository.updateServer(server.copy(
                    javaVersion = _javaVersion.value,
                    autoStart = _autoStart.value
                ))
                
                serverManager.syncFileToExecutionDir(server.id, "server.properties")
            }

            _uiEvent.emit(UiEvent.ShowToast("Propriedades salvas!"))
        }
    }

    private val _serverIconUpdate = MutableStateFlow(0L)
    val serverIconUpdate: StateFlow<Long> = _serverIconUpdate.asStateFlow()
    
    private val _serverIconPath = MutableStateFlow<String?>(null)
    val serverIconPath: StateFlow<String?> = _serverIconPath.asStateFlow()


    fun setServerIcon(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val server = repository.getServerById(serverId) ?: return@launch
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, 64, 64, true)
                    
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        if (server.uri != null) {
                             // SAF Logic
                             val rootUri = android.net.Uri.parse(server.uri)
                             val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, rootUri)
                             if (rootDoc != null && rootDoc.exists()) {
                                 // Delete existing if any
                                 rootDoc.findFile("server-icon.png")?.delete()
                                 // Create new
                                 val iconDoc = rootDoc.createFile("image/png", "server-icon.png")
                                 iconDoc?.let { doc ->
                                     context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                                         scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                     }
                                     log("Server icon updated via SAF: ${doc.uri}")
                                     _serverIconPath.value = doc.uri.toString()
                                     _serverIconUpdate.value = System.currentTimeMillis()
                                     _uiEvent.emit(UiEvent.ShowToast("Ícone atualizado!"))
                                     
                                     // Force sync to execution dir if server is running
                                     serverManager.syncFileToExecutionDir(server.id, "server-icon.png")
                                 }
                             }
                        } else {
                            // File Logic
                            val iconFile = File(server.path, "server-icon.png")
                            val outStream = java.io.FileOutputStream(iconFile)
                            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outStream)
                            outStream.close()
                            
                            log("Server icon updated: ${iconFile.absolutePath}")
                            _serverIconPath.value = iconFile.absolutePath
                            _serverIconUpdate.value = System.currentTimeMillis()
                            _uiEvent.emit(UiEvent.ShowToast("Ícone atualizado!"))

                            // Force sync to execution dir if server is running
                            serverManager.syncFileToExecutionDir(server.id, "server-icon.png")
                        }
                    }
                }
            } catch (e: Exception) {
                log("Error setting server icon: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun grantOp(username: String) {
        viewModelScope.launch {
            try {
                val server = repository.getServerById(serverId) ?: return@launch
                
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    // Pre-calculate content
                    val uuid = java.util.UUID.randomUUID().toString()
                    val opEntry = """
                        [
                          {
                            "uuid": "$uuid",
                            "name": "$username",
                            "level": 4,
                            "bypassesPlayerLimit": false
                          }
                        ]
                    """.trimIndent()
                    
                    if (server.uri != null) {
                         // SAF Logic
                         val rootUri = android.net.Uri.parse(server.uri)
                         val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, rootUri)
                         if (rootDoc != null && rootDoc.exists()) {
                             val opsJson = rootDoc.findFile("ops.json")
                             
                             if (opsJson != null || _onlineMode.value) {
                                 // Write to ops.json (create or overwrite)
                                 val target = opsJson ?: rootDoc.createFile("application/json", "ops.json")
                                 target?.let { 
                                     context.contentResolver.openOutputStream(it.uri, "wt")?.use { out ->
                                         out.write(opEntry.toByteArray()) 
                                     }
                                 }
                                 log("Granted OP to $username in ops.json (SAF)")
                             } else {
                                  // Fallback to ops.txt logic not implemented fully for SAF here, defaulting to JSON as it's standard
                                  // Or create ops.txt
                                  val opsTxt = rootDoc.findFile("ops.txt") ?: rootDoc.createFile("text/plain", "ops.txt")
                                  opsTxt?.let {
                                      context.contentResolver.openOutputStream(it.uri, "wt")?.use { out ->
                                          out.write(username.toByteArray())
                                      }
                                  }
                             }
                         }
                    } else {
                        // File Logic
                        val serverDir = File(server.path)
                        val opsJson = File(serverDir, "ops.json")
                        
                        if (opsJson.exists() || _onlineMode.value) {
                            opsJson.writeText(opEntry)
                            log("Granted OP to $username in ops.json")
                        } else {
                            val opsTxt = File(serverDir, "ops.txt")
                            opsTxt.writeText(username)
                            log("Granted OP to $username in ops.txt")
                         }
                     }
                     _uiEvent.emit(UiEvent.ShowToast("OP concedido a $username"))
                }
            } catch (e: Exception) {
                log("Error granting OP: ${e.message}")
                _uiEvent.emit(UiEvent.ShowToast("Erro ao conceder OP"))
            }
        }
    }
}
