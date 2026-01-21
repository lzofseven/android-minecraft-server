package com.lzofseven.mcserver.ui.screens.management

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lzofseven.mcserver.data.repository.ServerRepository
import com.lzofseven.mcserver.util.ServerPropertiesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context

@HiltViewModel
class ServerManagementViewModel @Inject constructor(
    private val repository: ServerRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val serverId: String = checkNotNull(savedStateHandle["serverId"])
    
    private val _motd = MutableStateFlow("")
    val motd: StateFlow<String> = _motd.asStateFlow()

    private val _maxPlayers = MutableStateFlow("20")
    val maxPlayers: StateFlow<String> = _maxPlayers.asStateFlow()

    private val _onlineMode = MutableStateFlow(true)
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

    // Performance
    private val _viewDistance = MutableStateFlow("10")
    val viewDistance: StateFlow<String> = _viewDistance.asStateFlow()
    private val _simulationDistance = MutableStateFlow("10")
    val simulationDistance: StateFlow<String> = _simulationDistance.asStateFlow()
    
    private var propertiesManager: ServerPropertiesManager? = null

    init {
        loadProperties()
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
                _onlineMode.value = props["online-mode"]?.toBoolean() ?: true
                _gameMode.value = props["gamemode"] ?: "survival"

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

                _viewDistance.value = props["view-distance"] ?: "10"
                _simulationDistance.value = props["simulation-distance"] ?: "10"
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

    fun setViewDistance(value: String) { _viewDistance.value = value }
    fun setSimulationDistance(value: String) { _simulationDistance.value = value }

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
                "view-distance" to _viewDistance.value,
                "simulation-distance" to _simulationDistance.value
            ))
        }
    }

    fun grantOp(username: String) {
        viewModelScope.launch {
            try {
                val server = repository.getServerById(serverId) ?: return@launch
                val serverDir = File(server.path)
                val opsJson = File(serverDir, "ops.json")
                
                // For simplicity, we create a basic ops.json entry
                // In a perfect world we'd use mojang API to get UUID, 
                // but for cracked/local servers, a random UUID often works or just the name for ops.txt
                
                if (opsJson.exists() || _onlineMode.value) {
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
                    opsJson.writeText(opEntry)
                    log("Granted OP to $username in ops.json")
                } else {
                    val opsTxt = File(serverDir, "ops.txt")
                    opsTxt.writeText(username)
                    log("Granted OP to $username in ops.txt")
                }
            } catch (e: Exception) {
                log("Error granting OP: ${e.message}")
            }
        }
    }
}
